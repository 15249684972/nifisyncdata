package org.apache.nifi.httpProcesssors;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import bean.KdmData;
import bean.KdmData2;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by coco1 on 2017/7/18.
 */
@SideEffectFree
@Tags({ "JSON", "SHA0W.PUB" })
@CapabilityDescription("Fetch value from json path.")
public class PutHttpKdmBean extends AbstractProcessor {
	private ComponentLog logger;
	private boolean flag = true;
	private String url;

	private List<PropertyDescriptor> properties;
	private Set<Relationship> relationships;

	public static final String MATCH_ATTR = "match";
	public static final PropertyDescriptor IP = new PropertyDescriptor.Builder().name("IP").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
	public static final PropertyDescriptor PORT = new PropertyDescriptor.Builder().name("PORT").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor QUERY_PATH = new PropertyDescriptor.Builder().name("QUERY_PATH")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
	public static final PropertyDescriptor STYLE = new PropertyDescriptor.Builder().name("STYLE").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
			.description("Succes relationship").build();

	@Override
	public void init(final ProcessorInitializationContext context) {
		ArrayList<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(IP);
		properties.add(PORT);
		properties.add(QUERY_PATH);
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
				|| descriptor.getName().equals("QUERY_PATH")|| descriptor.getName().equals("STYLE")) {
			flag=true;
		}

	}

	public String getUrl(ProcessContext processContext) {
		String ip = processContext.getProperty(IP).evaluateAttributeExpressions().getValue();
		String port = processContext.getProperty(PORT).evaluateAttributeExpressions().getValue();
		String queryPath = processContext.getProperty(QUERY_PATH).evaluateAttributeExpressions().getValue();
		String style = processContext.getProperty(STYLE).evaluateAttributeExpressions().getValue();
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("http://");
		stringBuilder.append(ip);
		stringBuilder.append(":");
		stringBuilder.append(port);
		stringBuilder.append(queryPath);
		stringBuilder.append("?alias=");
		stringBuilder.append(style);
		return stringBuilder.toString();

	}

	@Override
	public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {
		final AtomicReference<String> value = new AtomicReference<>();
		logger=getLogger();
		if (flag == true) {
			url = getUrl(processContext);
			flag = false;
		}
		logger.info("url="+url);
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
		if(results==null){
			logger.error("接收数据失败！");
		}
		List<KdmData> kdmDatas=getJsonToData(results);
		JSONArray jsonObject=JSONArray.fromObject(kdmDatas);
		String jsonData=jsonObject.toString();
		putJsonDataHttp(jsonData);
		flowFile = processSession.write(flowFile, out -> out.write(value.get().getBytes()));
		processSession.transfer(flowFile, SUCCESS);
	}
	public List<KdmData> getJsonToData(String jsonString) {
		JSONObject jsonObject = new JSONObject();
		Map map = (Map) jsonObject.fromObject(jsonString);
		Set set = map.keySet();
		Iterator ite = set.iterator();
		List<KdmData> kdmDatas=new ArrayList<KdmData>();
		while (ite.hasNext()) {
			List<KdmData2> kdmDatas2=new ArrayList<KdmData2>();
			String key = (String) ite.next();
			JSONObject jsonObject2 = JSONObject.fromObject(map.get(key));
			JSONObject jsonObject3 = (JSONObject) jsonObject2.fromObject(jsonObject2).get("data");
			Object time;
			Object value;
			try {
				time=jsonObject3.fromObject(jsonObject3).get("time");
			} catch (Exception e) {
				// TODO: handle exception
				time=0;
			}try {
				value=jsonObject3.fromObject(jsonObject3).get("value");
			} catch (Exception e) {
				// TODO: handle exception
				value=0.0;
			}
			KdmData2 kdmData2=new KdmData2();
			kdmData2.setTime(time);
			kdmData2.setValue(value);
			if (kdmDatas2.isEmpty()) {
				kdmDatas2.add(kdmData2);
			}
			kdmDatas2.add(kdmData2);
			KdmData kdmData=new KdmData();
			kdmData.setCode(key);
			kdmData.setData(kdmDatas2);
			kdmData.setState("CODE_NOT_EXIST");
			kdmDatas.add(kdmData);
		}
		return kdmDatas;
		// [ { "code": "HNAB.AB_TOT_TActPow", "data": [ { "time": 1526028317073,
				// "value": 2.2222 } ], "state": "CODE_NOT_EXIST" }, { "code":
				// "HNAB.AB_TOT_AINTLTMP", "data": [ { "time": 1526028317073, "value":
				// 3.3333 } ], "state": "CODE_NOT_EXIST" }]
	}
	public void putJsonDataHttp(String jsonData){
		CloseableHttpClient httpclient = HttpClients.createDefault();
		try {
			HttpPost httpPost = new HttpPost(url);
			// json数据{"username":"vip","password":"secret"}
			String jsonStr = jsonData;
			StringEntity se = new StringEntity(jsonStr);
			// se.setContentEncoding("UTF-8");
			se.setContentType("application/json");
			httpPost.setEntity(se);
			CloseableHttpResponse response2 = httpclient.execute(httpPost);
			try {
				logger.info(response2.getStatusLine().toString());
				HttpEntity entity2 = response2.getEntity();
				String res = EntityUtils.toString(entity2);
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
