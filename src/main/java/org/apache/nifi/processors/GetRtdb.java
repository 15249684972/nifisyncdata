package org.apache.nifi.processors;

import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.management.RuntimeErrorException;
import javax.naming.ldap.StartTlsRequest;

/**
 * Created by coco1 on 2017/7/18.
 */
@SideEffectFree
@Tags({ "KDM", "Real-time data" })
@CapabilityDescription("Fetch value from KDM-RTDB /v2/rtdata")
public class GetRtdb extends AbstractProcessor {
	private ComponentLog logger;
	private boolean flag = true;
	private String[] seqDatas;
	private int batch;
	private int PoolThreads;
	private int CodeCount;
	private String[] urls;
	private ExecutorService fixedThreadPool;
	private long totalCount;
	OkHttpClient okHttpclient;

	private List<PropertyDescriptor> properties;
	private Set<Relationship> relationships;

	public static final String MATCH_ATTR = "match";

	public static final AllowableValue KKS = new AllowableValue("KKS", "KKS", "传入的编码风格是KKS");
	public static final AllowableValue TAG = new AllowableValue("TAG", "TAG", "传入的编码风格是源编码");
	public static final AllowableValue MIX = new AllowableValue("MIX", "MIX", "传入的编码风格是混合KKS和源编码");

	public static final PropertyDescriptor IP = new PropertyDescriptor.Builder().name("IP").description("需要同步的rtdb地址")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
	public static final PropertyDescriptor PORT = new PropertyDescriptor.Builder().name("PORT")
			.description("需要同步的rtdb的端口号").required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor DATA_DIRECT_PATH = new PropertyDescriptor.Builder().name("DIRECT_PATH")
			.description("csv文件路径").required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor CODE_COUNT = new PropertyDescriptor.Builder().name("CODE_COUNT")
			.description("需要同步的历史数据批量大小").required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor POOL_THREADS = new PropertyDescriptor.Builder().name("POOL_THREADS")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor STYLE = new PropertyDescriptor.Builder().name("STYLE")
			.description("传入的编码风格，kks 或者tag ,或者是两者混合").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).allowableValues(KKS, TAG, MIX)
			.defaultValue(KKS.getValue()).build();

	public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
			.description("Succes relationship").build();

	@Override
	public void init(final ProcessorInitializationContext context) {
		ArrayList<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(IP);
		properties.add(PORT);
		properties.add(DATA_DIRECT_PATH);
		properties.add(CODE_COUNT);
		properties.add(POOL_THREADS);
		properties.add(STYLE);
		// 防止多线程ADD
		this.properties = Collections.unmodifiableList(properties);
		Set<Relationship> relationships = new HashSet<>();
		relationships.add(SUCCESS);
		// 防止多线程ADD
		this.relationships = Collections.unmodifiableSet(relationships);
	}
	@Override
	public void onPropertyModified(PropertyDescriptor descriptor, String oldValue, String newValue) {
		if (descriptor.getName().equals("IP") || descriptor.getName().equals("PORT")
				|| descriptor.getName().equals("QUERY_PATH") || descriptor.getName().equals("STYLE")) {
			flag = true;
		}
		if (descriptor.getName().equals("DIRECT_PATH")) {
			String directPath = newValue;
			seqDatas = getSeqDatas(directPath);
			flag = true;
		}
		if (descriptor.getName().equals("CODE_COUNT")) {
			CodeCount = Integer.parseInt(newValue);
			flag = true;
		}
		if (descriptor.getName().equals("POOL_THREADS")) {
			PoolThreads = Integer.parseInt(newValue);
			flag = true;
		}
	}
	public String[] getSeqDatas(String directPath) {
		StringBuilder seqCode = new StringBuilder();
		// BufferedReader br = null;
		BufferedReader in = null;
		StringBuilder strBulid = null;
		try {
			// br = new BufferedReader(new FileReader(new File(directPath)));
			in = new BufferedReader(new InputStreamReader(new FileInputStream(directPath), "UTF-8"));
			String s = null;
			while ((s = in.readLine()) != null) {
				seqCode.append(s);
				seqCode.append(",");
			}
			String seqData = seqCode.toString().substring(0, seqCode.toString().length() - 1);
			strBulid = new StringBuilder();
			String[] str = seqData.split(",");
			for (int i = 0; i < str.length; i++) {
				if ((i + 1) % 3 == 2) {
					strBulid.append(str[i]);
					strBulid.append(",");
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String[] Datas = strBulid.toString().substring(0, strBulid.toString().length() - 1).split(",");
		return Datas;
	}

	public String getUrl(ProcessContext processContext, String batchCode) throws UnsupportedEncodingException {
		String ip = processContext.getProperty(IP).evaluateAttributeExpressions().getValue();
		String port = processContext.getProperty(PORT).evaluateAttributeExpressions().getValue();
		String style = processContext.getProperty(STYLE).evaluateAttributeExpressions().getValue();
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("http://");
		stringBuilder.append(ip);
		stringBuilder.append(":");
		stringBuilder.append(port);
		stringBuilder.append("/v2/rtdata/latest");
		stringBuilder.append("?codes=");
		stringBuilder.append(batchCode.replace("#", "%23"));
		stringBuilder.append("&style=");
		stringBuilder.append(style);
		return stringBuilder.toString();

	}

	public void splitCode(ProcessContext processContext, int CodeCount) throws UnsupportedEncodingException {
		batch = seqDatas.length / CodeCount;
		urls = new String[batch];
		int batchCodeCounts1 = seqDatas.length / batch;
		logger.info("batch1:" + batchCodeCounts1);
		int batchCodeCounts2 = seqDatas.length / batch;
		int remainder = seqDatas.length % batch;
		for (int i = 0; i < batch; i++) {
			if (i == batch - 1) {
				batchCodeCounts1 = batchCodeCounts1 + remainder;
			}
			StringBuilder stringBuilder = new StringBuilder();
			for (int j = i * batchCodeCounts2; j < i * batchCodeCounts2 + batchCodeCounts1; j++) {
				stringBuilder.append(seqDatas[j]);
				stringBuilder.append(",");
			}
			String batchCode = stringBuilder.toString().substring(0, stringBuilder.toString().length() - 1);
			urls[i] = getUrl(processContext, batchCode);
		}
		if (fixedThreadPool != null) {
			fixedThreadPool.shutdown();
		}
		if (urls.length < PoolThreads) {
			PoolThreads = urls.length;
		}
		fixedThreadPool = Executors.newFixedThreadPool(PoolThreads);
		okHttpclient = new OkHttpClient();
		flag = false;
	}

	@Override
	public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {
		long onTriStartTime = System.currentTimeMillis();
		logger = getLogger();
		if (flag == true) {
			try {
				splitCode(processContext, CodeCount);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		ArrayList<Future<String>> futures = new ArrayList<Future<String>>();
		for (int i = 0; i < batch; i++) {
			String url = urls[i];
			Future<String> f = fixedThreadPool.submit(new Callable<String>() {
				@Override
				public String call() throws IOException {
					long startTime = System.currentTimeMillis();
					try {
						return getURLContent(url);
					} finally {
						logger.info("每次请求时间：" + (System.currentTimeMillis() - startTime));
					}
				}
			});
			futures.add(f);
		}
		for (Future<String> f : futures) {
			try {
				byte[] context = f.get().getBytes();
				FlowFile flowFile = processSession.create();
				flowFile = processSession.importFrom(new ByteArrayInputStream(context), flowFile);
				processSession.transfer(flowFile, SUCCESS);
				processSession.commit();
				totalCount = totalCount + 1;
				logger.info("发送总次数：" + totalCount);
			} catch (Exception e) {
				logger.error("发送数据失败！！！");
				e.printStackTrace();
			}
		}
		logger.info("oonTriTotalTime:" + (System.currentTimeMillis() - onTriStartTime));
	}
	public String getURLContent(String url){
		 CacheControl cc = new CacheControl.Builder().noStore().build();
		Request request = new Request.Builder().cacheControl(cc).url(url).build();
		String jsonData=null;
		Call call = okHttpclient.newCall(request);
		Response response=null;
		try {
			response = call.execute();
			int res=response.code();
			if (!(res==200)) {
				logger.error("查询数据失败！"+res);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (!response.isSuccessful()) {
			try {
				throw new IOException("Unexpected code " + response);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try {
			jsonData = response.body().string();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return jsonData;
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











