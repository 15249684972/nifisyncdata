package org.apache.nifi.httpProcesssors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.IOUtils;
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

import vzdb.FloatIdData;
import vzdb.FloatTagData;
import vzdb.Hdb;

/**
 * Created by coco1 on 2017/7/18.
 */
@SideEffectFree
@Tags({ "KDM", "SHA0W.PUB" })
@CapabilityDescription("Fetch value from kdm")
public class PuKdmId extends AbstractProcessor {
	private boolean flag = true;
	private Hdb rdb = null;
	private long totalData;
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

	public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
			.description("Succes relationship").build();

	@Override
	public void init(final ProcessorInitializationContext context) {
		ArrayList<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(DATA_IP);
		properties.add(DATA_PORT);
		properties.add(DATA_USER);
		properties.add(DATA_PASSWORD);
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
	}

	public Hdb getConnect(ProcessContext processContext, ProcessSession processSession) {
		String ip = processContext.getProperty(DATA_IP).evaluateAttributeExpressions().getValue();
		int port = Integer.parseInt(processContext.getProperty(DATA_PORT).evaluateAttributeExpressions().getValue());
		String user = processContext.getProperty(DATA_USER).evaluateAttributeExpressions().getValue();
		String password = processContext.getProperty(DATA_PASSWORD).evaluateAttributeExpressions().getValue();
		rdb = new Hdb();
		int ret = rdb.connect(ip, port, user, password);
		return rdb;

	}

	public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {
		ComponentLog logger = getLogger();
		final AtomicReference<String> value = new AtomicReference<>();
		if (flag == true) {
			rdb = getConnect(processContext, processSession);
			flag = false;
		}
		FlowFile flowFile = processSession.get();
		processSession.read(flowFile, in -> {
			try {
				String data = IOUtils.toString(in);
				value.set(data);
			} catch (Exception ex) {
				ex.printStackTrace();
				logger.error("Failed to read .");
			}
		});
		String results = value.get();
		String[] str = results.split("\n");
		List<FloatIdData> datas = new ArrayList<>();
		for (int i = 0; i < str.length; i++) {
			String[] strHandle = str[i].split(",");
			FloatIdData data = new FloatIdData();
			data.id = Integer.parseInt(strHandle[0]);
			data.tm = Integer.parseInt(strHandle[1]);
			data.val = Float.parseFloat(strHandle[2]);
			data.flag = (byte) Integer.parseInt(strHandle[3]);
			datas.add(data);
		}
		try {
			FloatIdData[] floatIdData = datas.toArray(new FloatIdData[datas.size()]);
			long time2=System.currentTimeMillis();
			int ret = rdb.writeFloatRealDatasByIds(floatIdData);
			logger.info("插入数据时间："+(System.currentTimeMillis()-time2));
			if (ret == 1) {
				logger.info("批数量:" + datas.size());
				totalData = totalData + datas.size();
				logger.info("总数量:" + totalData);
			}else{
				logger.error("插入数据库失败");
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
		flowFile = processSession.write(flowFile, out -> out.write(value.get().getBytes()));
		processSession.transfer(flowFile, SUCCESS);
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
