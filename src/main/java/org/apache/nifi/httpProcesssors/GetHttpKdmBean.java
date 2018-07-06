package org.apache.nifi.httpProcesssors;

import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;

import bean.KdmData;
import bean.KdmData2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * Created by coco1 on 2017/7/18.
 */
@SideEffectFree
@Tags({ "JSON", "SHA0W.PUB" })
@CapabilityDescription("Fetch value from json path.")
public class GetHttpKdmBean extends AbstractProcessor {
	private ComponentLog logger;
	private boolean flag = true;
	private String url;
	private String seqDatas;
	private List<PropertyDescriptor> properties;
	private Set<Relationship> relationships;

	public static final String MATCH_ATTR = "match";
	public static final PropertyDescriptor IP = new PropertyDescriptor.Builder().name("IP").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
	public static final PropertyDescriptor PORT = new PropertyDescriptor.Builder().name("PORT").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor QUERY_PATH = new PropertyDescriptor.Builder().name("QUERY_PATH")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
	public static final PropertyDescriptor DATA_DIRECT_PATH = new PropertyDescriptor.Builder().name("DIRECT_PATH")
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
		properties.add(DATA_DIRECT_PATH);
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
				|| descriptor.getName().equals("QUERY_PATH") || descriptor.getName().equals("DIRECT_PATH")
				|| descriptor.getName().equals("STYLE")) {
			flag=true;
		}
		if (descriptor.getName().equals("DIRECT_PATH")) {
			String directPath = newValue;
			seqDatas = getSeqDatas(directPath);
			//logger.info("编码长度：  " + seqDatas.length);
		}
	}
	public String getSeqDatas(String directPath) {
		StringBuilder seqCode = new StringBuilder();
		BufferedReader br = null;
		StringBuilder strBulid = null;
		try {
			br = new BufferedReader(new FileReader(new File(directPath)));
			String s = null;
			while ((s = br.readLine()) != null) {
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
			br.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String Datas = strBulid.toString().substring(0, strBulid.toString().length() - 1);
		return Datas;
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
		stringBuilder.append("?codes=");
		stringBuilder.append(seqDatas.replace("#", "%23"));
		stringBuilder.append("&style=");
		stringBuilder.append(style);
		return stringBuilder.toString();
	}

	@Override
	public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {
		logger=getLogger();
		if (flag == true) {
			url = getUrl(processContext);
			flag = false;
		}
		logger.info("url:"+url);
		String jsonString=null;
		try {
			jsonString=getURLContent(url);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		byte[] context = jsonString.toString().getBytes();
		FlowFile flowFile = processSession.create();
		flowFile = processSession.append(flowFile, new OutputStreamCallback() {
			@Override
			public void process(OutputStream out) throws IOException {
				// TODO Auto-generated method stub
				out.write(context);
			}
		});
		processSession.transfer(flowFile, SUCCESS);
	}
	
	private String getURLContent(String url2) throws IOException {
		URL url = new URL(url2);
		HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
		httpConn.setRequestMethod("GET");
		httpConn.connect();
		BufferedReader reader = new BufferedReader(new InputStreamReader(httpConn.getInputStream()));
		String line;
		StringBuffer buffer = new StringBuffer();
		while ((line = reader.readLine()) != null) {
			buffer.append(line);
		}
		reader.close();
		httpConn.disconnect();
		return buffer.toString();
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
