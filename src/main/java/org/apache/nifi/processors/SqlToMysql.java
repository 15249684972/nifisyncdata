package org.apache.nifi.processors;

import org.apache.commons.io.IOUtils;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by coco1 on 2017/7/18.
 */
@SideEffectFree
@Tags({ "JSON", "SHA0W.PUB" })
@CapabilityDescription("Fetch value from json path.")
public class SqlToMysql extends AbstractProcessor {
    private String table;
    private String column;
	private List<PropertyDescriptor> properties;
	private Set<Relationship> relationships;
	public static final PropertyDescriptor TABLE = new PropertyDescriptor.Builder().name("TABLE")
			.description("数据库表")
			.required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();
	public static final PropertyDescriptor COLUMN = new PropertyDescriptor.Builder().name("COLUMN")
			.description("数据库表的列")
			.required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
			.description("Succes relationship").build();
	
	@Override
	public void init(final ProcessorInitializationContext context) {
		ArrayList<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(TABLE);
		properties.add(COLUMN);
		// 防止多线程ADD
		this.properties = Collections.unmodifiableList(properties);
		Set<Relationship> relationships = new HashSet<>();
		relationships.add(SUCCESS);
		// 防止多线程ADD
		this.relationships = Collections.unmodifiableSet(relationships);
	}
	public void onPropertyModified(PropertyDescriptor descriptor, String oldValue, String newValue) {
		if (descriptor.equals(TABLE)){
			table=newValue;
		}
		if (descriptor.equals(COLUMN)) {
			column=newValue;
		}
		
	}
	@Override
	public void onTrigger(ProcessContext processContext, ProcessSession processSession) throws ProcessException {
		 ComponentLog logger=getLogger();
			final AtomicReference<String> value = new AtomicReference<>();
			FlowFile flowFile = processSession.get();
 			processSession.read(flowFile, in -> {
				try {
					String data = IOUtils.toString(in);
					value.set(data);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			});
		String data = value.get();
		String jsonData="insert into "+ table +" ("+ column +") values ('"+ data +"')";
		byte[] context = jsonData.toString().getBytes();
		flowFile = processSession.importFrom(new ByteArrayInputStream(context), flowFile);
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
