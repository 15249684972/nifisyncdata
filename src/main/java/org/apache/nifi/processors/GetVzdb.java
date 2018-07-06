package org.apache.nifi.processors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

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
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import vzdb.FloatRealData;
import vzdb.FloatRealDataListHolder;
import vzdb.Hdb;

/**
 * Created by coco1 on 2017/7/18.
 */
@SideEffectFree
@Tags({ "KDM", "SHA0W.PUB" })
@CapabilityDescription("Fetch value from kdm")
public class GetVzdb extends AbstractProcessor {
	private ComponentLog logger;
	private boolean flag = true;
	private Hdb rdb = null;
	private String[] seqDatas;
	private int batchSize = 1;
	private long getKdmtotalData;

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
	public static final PropertyDescriptor BATCH_SIZE = new PropertyDescriptor.Builder().name("Batch_size")
			.required(false).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
	public static final PropertyDescriptor MESSAGE_DEMARCATOR = new PropertyDescriptor.Builder()
			.name("MESSAGE_DEMARCATOR").required(false).addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
			.defaultValue("\\n").build();

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
		properties.add(BATCH_SIZE);
		properties.add(MESSAGE_DEMARCATOR);
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
			logger.info("编码长度：  " + seqDatas.length);
		}
		if (descriptor.getName().equals("Batch_size")) {
			batchSize = Integer.parseInt(newValue);
		}
	}

	public String[] getSeqDatas(String directPath) {
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
		String[] Datas = strBulid.toString().substring(0, strBulid.toString().length() - 1).split(",");
		return Datas;
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
		long startTime = System.currentTimeMillis();
		logger = getLogger();
		logger.info("onTrigger:start:  " + startTime);
		if (flag == true) {
			rdb = getConnect(processContext, processSession);
			flag = false;
		}
		String demarcator = processContext.getProperty(MESSAGE_DEMARCATOR).evaluateAttributeExpressions().getValue()
				.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
		logger.info("demarcator:" + demarcator);
		FloatRealDataListHolder listHolder = new FloatRealDataListHolder();
		long time2 = System.currentTimeMillis();
		int back = rdb.readFloatRealDatasByTags(seqDatas, listHolder);
		FloatRealData[] dataLists = listHolder.value;
		logger.info("查库时间：" + (System.currentTimeMillis() - time2));
		int multiple = seqDatas.length / batchSize;
		int remainder = seqDatas.length % batchSize;
		int batch = batchSize;
		int batch2 = batchSize;
		FlowFile flowFile = null;
		long time = 0;
		for (int j = 0; j <= multiple; j++) {
			StringBuilder stringBuilder = new StringBuilder();
			if (j == multiple) {
				batch2 = remainder;
			}
			for (int i = j * batch; i < (j * batch + batch2); i++) {
				if (i != j * batch) {
					stringBuilder.append(demarcator);
				}
				if (time < dataLists[i].tm) {
					time = dataLists[i].tm;
					logger.info("time=" + time);
				}
				stringBuilder.append(seqDatas[i]);
				stringBuilder.append(",");
				stringBuilder.append(dataLists[i].tm);
				stringBuilder.append(",");
				stringBuilder.append(dataLists[i].val);
				stringBuilder.append(",");
				stringBuilder.append(dataLists[i].state);
			}
			byte[] context = stringBuilder.toString().getBytes();
			flowFile = processSession.create();
			flowFile = processSession.append(flowFile, new OutputStreamCallback() {
				@Override
				public void process(OutputStream out) throws IOException {
					// TODO Auto-generated method stub
					out.write(context);
				}
			});
			// logger.info("批处理数量： " + batch2 + ",time=" + time);
			processSession.transfer(flowFile, SUCCESS);
			getKdmtotalData = getKdmtotalData + batch2;
		}
		logger.info("kdm2totalData:" + getKdmtotalData);
		long endTime = System.currentTimeMillis();
		logger.info("时间差" + (endTime - startTime));
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
