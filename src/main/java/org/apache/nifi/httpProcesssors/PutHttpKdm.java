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
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by coco1 on 2017/7/18.
 */
@SideEffectFree
@Tags({ "JSON", "SHA0W.PUB" })
@CapabilityDescription("Fetch value from json path.")
public class PutHttpKdm extends AbstractProcessor {
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
	    ComponentLog logger=getLogger();
		final AtomicReference<String> value = new AtomicReference<>();
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
		logger.info(results);
		putJsonDataHttp(results);
		flowFile = processSession.write(flowFile, out -> out.write(value.get().getBytes()));
		processSession.transfer(flowFile, SUCCESS);
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
				//logger.info(response2.getStatusLine().toString());
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
