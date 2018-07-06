package org.apache.nifi.httpProcesssors;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import bean.JsonKdm;
import bean.KdmData;
import bean.KdmData2;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by coco1 on 2017/7/18.
 */
@SideEffectFree
@Tags({ "JSON", "SHA0W.PUB" })
@CapabilityDescription("Fetch value from json path.")
public class PutHttpKdmThread2 extends AbstractProcessor {
	private ComponentLog logger;
	private boolean flag = true;
	private String url;
	private long totalCount;
	OkHttpClient httpClient;
	
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
		long onTriStartTime=System.currentTimeMillis();
		final AtomicReference<String> value = new AtomicReference<>();
		logger=getLogger();
		if (flag == true) {
			url = getUrl(processContext);
			httpClient = new OkHttpClient();
			flag = false;
		}
		logger.info("url="+url);
		FlowFile flowFile = processSession.get();
		try {
			processSession.read(flowFile, in -> {
				try {
					String data = IOUtils.toString(in);
					value.set(data);
					logger.info("data数据："+data);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		String results = value.get();
		if(results==null){
			logger.error("接收数据失败！");
		}
		String[] strs=results.split("\n");
		for (int i = 0; i < strs.length; i++) {
			String jsonData=null;
			try {
				jsonData=getJsonToData(strs[i]);
				putJsonDataHttp(jsonData);
			} catch (Exception e) {
				// TODO: handle exception
				logger.error("接收数据失败！！！："+e+jsonData);
			}
		}
		flowFile = processSession.write(flowFile, out -> out.write(value.get().getBytes()));
		processSession.transfer(flowFile, SUCCESS);
		logger.info("oonTriTotalTime:"+(System.currentTimeMillis()-onTriStartTime));
	}
	public String getJsonToData(String jsonString) {
			Gson sGson=new Gson();
			JsonKdm map = sGson.fromJson(jsonString, new TypeToken<JsonKdm>() {
			}.getType());
			Iterator<Entry<String, HashMap<String, KdmData2>>> ite=map.entrySet().iterator();
			List<KdmData> kdmDataList=new ArrayList<KdmData>();
			while(ite.hasNext()){
				KdmData kdmData=new KdmData();
				Entry<String, HashMap<String, KdmData2>> next = ite.next();
				String code=next.getKey();
				HashMap<String, KdmData2> hashMap=next.getValue();
				Iterator<Entry<String,KdmData2>> ite1=hashMap.entrySet().iterator();
				kdmData.setCode(code);
				List<KdmData2> kdmData2List=new ArrayList<KdmData2>();
				while(ite1.hasNext()){
					//KdmData2 kdmData2=new KdmData2();
					KdmData2 kdmData2=ite1.next().getValue();
					kdmData2List.add(kdmData2);
				}
				kdmData.setData(kdmData2List);
				kdmData.setState("CODE_NOT_EXIST");
				kdmDataList.add(kdmData);
			}
			Gson sGson2=new Gson();
			String result=sGson2.toJson(kdmDataList).toString();
		return result;
	}
	public void putJsonDataHttp(String jsonData) throws IOException{
			  RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonData);
			  Request request = new Request.Builder()
			      .url(url)
			      .post(body)
			      .build();
			  Response response = httpClient.newCall(request).execute();
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
