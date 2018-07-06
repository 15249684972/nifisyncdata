package org.apache.nifi.processors;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bean.JsonKdm;
import bean.KdmData;
import bean.KdmData2;

public class PutKdm extends AbstractProcessor{
	private ComponentLog logger;
	private boolean flag = true;
	private String url;
	private long totalCount;
	private int PoolThreads;
	private ExecutorService fixedThreadPool;
	
	private List<PropertyDescriptor> properties;
	private Set<Relationship> relationships;

	public static final String MATCH_ATTR = "match";
	public static final PropertyDescriptor IP = new PropertyDescriptor.Builder().name("IP").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor PORT = new PropertyDescriptor.Builder().name("PORT").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor QUERY_PATH = new PropertyDescriptor.Builder().name("QUERY_PATH")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor POOL_THREADS = new PropertyDescriptor.Builder().name("POOL_THREADS")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
			.description("Succes relationship").build();

	@Override
	public void init(final ProcessorInitializationContext context) {
		ArrayList<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(IP);
		properties.add(PORT);
		properties.add(QUERY_PATH);
		properties.add(POOL_THREADS);
		// 防止多线程ADD
		this.properties = Collections.unmodifiableList(properties);
		Set<Relationship> relationships = new HashSet<>();
		relationships.add(SUCCESS);
		// 防止多线程ADD
		this.relationships = Collections.unmodifiableSet(relationships);
	}

	@Override
	public void onPropertyModified(PropertyDescriptor descriptor, String oldValue, String newValue) {
		if (descriptor.equals(IP) || descriptor.equals(PORT)
				|| descriptor.equals(QUERY_PATH)) {
			flag = true;
		}
		if (descriptor.equals(POOL_THREADS)) {
			PoolThreads = Integer.parseInt(newValue);
			flag = true;
		}
	}

	public String getUrl(ProcessContext processContext) {
		String ip = processContext.getProperty(IP).evaluateAttributeExpressions().getValue();
		String port = processContext.getProperty(PORT).evaluateAttributeExpressions().getValue();
		String queryPath = processContext.getProperty(QUERY_PATH).evaluateAttributeExpressions().getValue();
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("http://");
		stringBuilder.append(ip);
		stringBuilder.append(":");
		stringBuilder.append(port);
		stringBuilder.append(queryPath);
		return stringBuilder.toString();
	}

	private void initMethon(ProcessContext processContext) {
		url = getUrl(processContext);
		fixedThreadPool = Executors.newFixedThreadPool(PoolThreads);
	}

	@Override
	public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {
		long onTriStartTime = System.currentTimeMillis();
		final AtomicReference<String> value = new AtomicReference<>();
		logger = getLogger();
		if (flag == true) {
			initMethon(processContext);
			flag = false;
		}
		logger.info("url=" + url);
		FlowFile flowFile = processSession.get();
		processSession.read(flowFile, in -> {
			try {
				String data = IOUtils.toString(in);
				value.set(data);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
		String results = value.get();
		if (results == null) {
			logger.error("接收数据失败！");
		}
		String[] strs = results.split("\n");
		ArrayList<Future<String>> futures = new ArrayList<Future<String>>();
		for (int i = 0; i < strs.length; i++) {
			String jsonData = getJsonToData(strs[i]);
			Future<String> f = fixedThreadPool.submit(new Callable<String>() {
				@Override
				public String call() throws Exception {
					long startTime = System.currentTimeMillis();
					try {
						return putJsonDataHttp(jsonData);
					} finally {
						logger.info("每次请求时间：" + (System.currentTimeMillis() - startTime));
					}
				}
			});
			futures.add(f);
		}
		for (Future<String> f : futures) {
			try {
				if (f.get().equals("{}")) {
					totalCount=totalCount+1;
				}
			} catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}
		}
		logger.info("写入数据总量："+totalCount);
		flowFile = processSession.write(flowFile, out -> out.write(value.get().getBytes()));
		processSession.transfer(flowFile, SUCCESS);
		logger.info("oonTriTotalTime:" + (System.currentTimeMillis() - onTriStartTime));
	}

	public String getJsonToData(String jsonString) {
		// Gson sGson=new Gson();
		Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
		JsonKdm map = null;
		try {
			map = gson.fromJson(jsonString, JsonKdm.class);
		} catch (Exception e) {
			// TODO: handle exception
			logger.error("json数据转换失败！！！" + jsonString, e);
		}

		Iterator<Entry<String, HashMap<String, KdmData2>>> ite = map.entrySet().iterator();
		List<KdmData> kdmDataList = new ArrayList<KdmData>();
		while (ite.hasNext()) {
			KdmData kdmData = new KdmData();
			Entry<String, HashMap<String, KdmData2>> next = ite.next();
			String code = next.getKey();
			HashMap<String, KdmData2> hashMap = next.getValue();
			Iterator<Entry<String, KdmData2>> ite1 = hashMap.entrySet().iterator();
			kdmData.setCode(code);
			List<KdmData2> kdmData2List = new ArrayList<KdmData2>();
			while (ite1.hasNext()) {
				// KdmData2 kdmData2=new KdmData2();
				KdmData2 kdmData2 = ite1.next().getValue();
				kdmData2List.add(kdmData2);
			}
			kdmData.setData(kdmData2List);
			kdmData.setState("CODE_NOT_EXIST");
			kdmDataList.add(kdmData);
		}
		Gson sGson2 = new Gson();
		String result = sGson2.toJson(kdmDataList).toString();
		return result;
	}

	public String putJsonDataHttp(String jsonData) throws UnsupportedEncodingException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		String res = null;
		try {
			HttpPost httpPost = new HttpPost(url);
			StringEntity se = new StringEntity(jsonData, Charset.forName("utf-8"));
			se.setContentType("application/json;charset=utf-8");
			httpPost.setEntity(se);
			CloseableHttpResponse response2 = httpclient.execute(httpPost);
			try {
				HttpEntity entity2 = response2.getEntity();
				res = EntityUtils.toString(entity2);
				if (!res.equals("{}")) {
					logger.error("写入数据失败！"+res);
				}
			} finally {
				response2.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				httpclient.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return res;
	}

	@Override
	public Set<Relationship> getRelationships() {
		return relationships;
	}

	@Override
	public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return properties;
	}
}
