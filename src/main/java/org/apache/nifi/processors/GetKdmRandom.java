package org.apache.nifi.processors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
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
import vzdb.FloatTagData;
import vzdb.Hdb;

/**
 * Created by coco1 on 2017/7/18.
 */
@SideEffectFree
@Tags({ "KDM", "SHA0W.PUB" })
@CapabilityDescription("Fetch value from kdm")
public class GetKdmRandom extends AbstractProcessor {
	private ComponentLog logger;
	private boolean flag = true;
	private Hdb rdb = null;
	private List<String> seqDatas;
	private float sendCounts=0;

	private List<PropertyDescriptor> properties;
	private Set<Relationship> relationships;

	public static final String MATCH_ATTR = "match";

	public static final PropertyDescriptor DATA_IP = new PropertyDescriptor.Builder().name("IP").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
	public static final PropertyDescriptor DATA_PORT = new PropertyDescriptor.Builder().name("PORT").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor DATA_USER = new PropertyDescriptor.Builder().name("USER").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor DATA_PASSWORD = new PropertyDescriptor.Builder().name("PASSWORD")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
	public static final PropertyDescriptor DATA_DIRECT_PATH = new PropertyDescriptor.Builder().name("DIRECT_PATH")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
			.description("Succes relationship").build();

	@Override
	public void init(final ProcessorInitializationContext context) {
		ArrayList<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(DATA_IP);
		properties.add(DATA_PORT);
		properties.add(DATA_USER);
		properties.add(DATA_PASSWORD);
		properties.add(DATA_DIRECT_PATH);
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
				|| descriptor.getName().equals("USER") || descriptor.getName().equals("PASSWORD")) {
			rdb.disconnect();
			flag = true;
		}
		if (descriptor.getName().equals("DIRECT_PATH")) {
			String directPath = newValue;
			seqDatas = getSeqDatas(directPath);
			logger.info("编码长度：  " + seqDatas.size());
			flag = true;
		}
	}

	public List<String> getSeqDatas(String directPath) {
		StringBuilder seqCode = new StringBuilder();
		BufferedReader br = null;
		List<String> list = null;
		try {
			br = new BufferedReader(new FileReader(new File(directPath)));
			String s = null;
			while ((s = br.readLine()) != null) {
				seqCode.append(s);
				seqCode.append(",");
			}
			String seqData = seqCode.toString().substring(0, seqCode.toString().length() - 1);
			list = new ArrayList<>();
			String[] str = seqData.split(",");
			for (int i = 0; i < str.length; i++) {
				if ((i + 1) % 3 == 2) {
					list.add(str[i]);
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

		return list;
	}

	public Hdb getConnect(ProcessContext processContext, ProcessSession processSession) {
		String ip = processContext.getProperty(DATA_IP).evaluateAttributeExpressions().getValue();
		int port = Integer.parseInt(processContext.getProperty(DATA_PORT).evaluateAttributeExpressions().getValue());
		String user = processContext.getProperty(DATA_USER).evaluateAttributeExpressions().getValue();
		String password = processContext.getProperty(DATA_PASSWORD).evaluateAttributeExpressions().getValue();
		rdb = new Hdb();
		int ret = rdb.connect(ip, port, user, password);
		if (ret == 1) {
			logger.info("数据库连接成功：  " + ret);
		} else {
			logger.info("数据库连接失败：  " + ret);
		}
		return rdb;

	}

	public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {
		long ontriStartTime = System.currentTimeMillis();
		logger = getLogger();
		if (flag == true) {
			rdb = getConnect(processContext, processSession);
			flag = false;
		}
		List<FloatTagData> datas = new ArrayList<>();
		sendCounts = sendCounts+1;
		long dataTime=new Date().getTime();
		for (String string : seqDatas) {
			FloatTagData data = new FloatTagData();
			data.tag = string;
			data.tm = (int) (dataTime/1000);
			data.val =sendCounts;
			data.flag = (byte)1;
			datas.add(data);
		}
		try {
			FloatTagData[] floatTagData = datas.toArray(new FloatTagData[datas.size()]);
			long writeTime=System.currentTimeMillis();
			int ret = rdb.writeFloatRealDatasByTags(floatTagData);
			logger.info("写入数据耗时时间："+(System.currentTimeMillis()-writeTime));
			if (ret == 1) {
				logger.info("写入总数量:" + sendCounts);
			}else{
				logger.error("插入数据库失败");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		FlowFile flowFile=processSession.create();
		flowFile = processSession.write(flowFile, out -> out.write("".getBytes()));
		processSession.transfer(flowFile, SUCCESS);
		logger.info("ontrigger时间："+(System.currentTimeMillis()-ontriStartTime));
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
