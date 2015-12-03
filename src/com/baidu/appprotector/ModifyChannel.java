package com.baidu.appprotector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;

import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlVisitor;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

/*
 * ModifyChannel
 * 
 * Operates directly on the input AXML file, modify the channel to the new value.
 * Write the output data to a new AXML file.
 */
public class ModifyChannel {
	private static final String VERSION = "1.0.0";
	private static final String NS = "http://schemas.android.com/apk/res/android";
	
	// Some APKs may have malformed AndroidManifest.xml, which do not have namespace and name in some attributes.
	// However, the resource ID of the attribute must be specified so that it can be correctly parsed.
	// So we can simply use resource ID to recover attribute namespace and name.
	// Please refer to https://code.google.com/p/android-apktool/issues/detail?id=512 for details.
	
	// Attribute constants, as found in android.R.attr
	//private static final int NAME_ATTR = 0x01010003;
	
	//private static final int CLASS_TYPE_NORMAL = 0;
	//private static final int CLASS_TYPE_ACTIVITY = 1;
	//private static final int CLASS_TYPE_SERVICE = 2;
	//private static final int CLASS_TYPE_RECEIVER = 3;
	
	private static byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);

        long length = file.length();
        byte[] bytes = new byte[(int)length];

        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
               && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }

        is.close();

        return bytes;	
	}
	
	private static void writeBytesToFile(byte[] data, File file) throws IOException {
		OutputStream os = new FileOutputStream(file);
		os.write(data);
		os.close();
	}

	public static boolean isNumeric(String str){  
	    Pattern pattern = Pattern.compile("[0-9]*");  
	    return pattern.matcher(str).matches();     
	}
	
	private static void printUsage() {
		System.out.println("Usage: java -jar ApkChannel.jar Tag Value Value-type Input_xml Output_xml");
		System.out.println("\t Tag: 要修改的渠道信息meta-data项，android:name");
		System.out.println("\t Value: 修改原值为value, android:value");
		System.out.println("\t Value-type: 指定android:value的类型，可以是数字或字符串");
		System.out.println("\t\t1.string 渠道号为字符串，将Value以字符串类型替换");
		System.out.println("\t\t2.int 渠道号为数字，将Value以数字类型替换。参数Value必须全是数字字符");
		System.out.println("\t Input_xml: 原AndroidManifest.xml文件");
		System.out.println("\t Output_xml: 输出修改的AndroidManifest.xml文件");
		System.out.println("\n");
		System.out.println("注意！！");
		System.out.println("1.如果Value-type指定类型为int, 要求Value参数必须是全数字字符 ");
		System.out.println("2.如果Value-type指定是字符串,Value参数即使是数字也会被认定修改为Value的字符串");
		System.out.println("更多关于manifest中meta-data项的信息：http://developer.android.com/guide/topics/manifest/meta-data-element.html");
	}
	
	public static void main(String[] args) {
		System.out.println("ApkChannel Version" + VERSION);
		if (args.length != 5) {
			printUsage();
			System.exit(-1);
		}
		
		final String targetTag = args[0];
		final String targetVal = args[1];
		final String valueType = args[2];
		String inputXml = args[3];
		String outputXml = args[4];
		
		System.out.println("input:" + inputXml + "; output:" + outputXml + "; target:" + targetTag + "; value:" + targetVal + "; value-type:" + valueType + "\n");
		
		if (valueType.equalsIgnoreCase("int") && isNumeric(targetVal)) {
			System.out.println("Parameter error: targetVal is not INT");
			printUsage();
			return;
		}
		
		
		byte[] origAndroidManifestData = null;
		byte[] newAndroidManifestData = null;
		try {
			origAndroidManifestData = getBytesFromFile(new File(inputXml));
		} catch (IOException e) {
			System.out.println("failed to read input xml file!");
			System.exit(-1);
		}
		
        AxmlReader ar = new AxmlReader(origAndroidManifestData);
        AxmlWriter aw = new AxmlWriter();
		
        try {
			ar.accept(new AxmlVisitor(aw) {
	            public NodeVisitor child(String ns, String name) {// manifest
	                NodeVisitor nv = super.child(ns, name);
	                return new NodeVisitor(nv) {
	                	String mPackageName = "";
	                	@Override
                        public void attr(String ns, String name, int resourceId, int type, Object obj) {
	                		if ("package".equals(name)) {
	                			mPackageName = (String) obj;
	                			System.out.println("AxmlVisitor:attr:packageName:" + mPackageName);
	                		}
	                		super.attr(ns, name, resourceId, type, obj);
	                	}
	                	public NodeVisitor child(String ns, String name) {	//application
	                		System.out.println("NodeVisitor:child:node_name=" + name);
	                		if ("application".equals(name)) {
	                			
                                return new NodeVisitor(super.child(ns, name)) {
                                	private String mOrigAppClassName = "";
                                	
                                	@Override
                                    public NodeVisitor child(String ns, String name) {	//meta-data
                                		if ("meta-data".equals(name)) {	
                                    		System.out.println("\n\n\tNodeVisitor:child:[name=" + name + "]");
                                    		
                                    		return new NodeVisitor(super.child(ns, name)) {	//name||value
                                    			private boolean targetFound = false;
                                    			public void attr(String ns, String name, int resourceId, int type, Object obj) {
                                    				System.out.println("\t\tNodeVisitor:attr:[name:" + name + ";\tresourceId:" + resourceId + ";\ttype:" + type + ";\tobject:" + obj.getClass().toString() + "]");
                                    				
                                    				if(NS.equals(ns) && "name".equals(name)) {
                                    					String tag = (String) obj;
                                    					System.out.println("\t\t\tandroid:name=" + tag);
                                    					if (tag.equals(targetTag)) {
                                    						targetFound = true;
                                    						System.out.println("\t\t\t***Target [" + targetTag + "] found***");
                                    					}
                                    					super.attr(ns, name, resourceId, type, obj);
                                    				} else if (NS.equals(ns) && "value".equals(name)) {	//if(NS.equals(ns) && "name".equals(name)) {
                                    					System.out.println("\t\t\tandroid:value=" + obj);
                                    					
                                    					String oldTypeStr = "";
                                    					switch (type) {
                                    					case NodeVisitor.TYPE_FIRST_INT:
                                    						System.out.println("\t\t\tvalue_type:TYPE_FIRST_INT, target value is integer");
                                    						oldTypeStr = "TYPE_FIRST_INT";
                                    						break;
                                    					case NodeVisitor.TYPE_INT_BOOLEAN:
                                    						System.out.println("\t\t\tvalue_type:TYPE_INT_BOOLEAN, target value is boolean");
                                    						oldTypeStr = "TYPE_INT_BOOLEAN";
                                    						break;
                                    					case NodeVisitor.TYPE_INT_HEX:
                                    						System.out.println("\t\t\tvalue_type:TYPE_INT_HEX, target value is hex");
                                    						oldTypeStr = "TYPE_INT_HEX";
                                    						break;
                                    					case NodeVisitor.TYPE_REFERENCE:
                                    						System.out.println("\t\t\tvalue_type:TYPE_REFERENCE, target value is reference");
                                    						oldTypeStr = "TYPE_REFERENCE";
                                    						break;
                                    					case NodeVisitor.TYPE_STRING:
                                    						System.out.println("\t\t\tvalue_type:TYPE_STRING, target value is string");
                                    						oldTypeStr = "STRING";
                                    						break;
                                    					}
                                    					
                                    					int newValueType = valueType.equalsIgnoreCase("int") ? NodeVisitor.TYPE_FIRST_INT : NodeVisitor.TYPE_STRING;
                                    					if (targetFound) {
                                        					System.out.println("\t\t\tnew_value_type:" + (valueType.equalsIgnoreCase("int") ? "TYPE_FIRST_INT" : "TYPE_STRING"));
                                    						System.out.println("\t\t\t***Target value[" + obj + "(" + oldTypeStr + ")" + " --> " + targetVal + "(" + valueType + ")]***");
                                    		
                                    						super.attr(ns, name, resourceId, newValueType, (newValueType == NodeVisitor.TYPE_FIRST_INT) ? Integer.parseInt(targetVal) : targetVal);
                                    					} else {
                                    						super.attr(ns, name, resourceId, type, obj);
                                    					}
                                    				}	//else if (NS.equals(ns) && "value".equals(name)) {
                                    				
                                    			}	//public void attr(String ns, String name, int resourceId, int type, Object obj) {
                                    		};	//return new NodeVisitor(super.child(ns, name)) {	//name||value
                                    		
                                    	}
                                    	return super.child(ns, name);
                                    }
                                	
                                	
                                    @Override
                                    public void attr(String ns, String name, int resourceId, int type, Object obj) {
                                        System.out.println("\tNodeVisitor:attr:[name:" + name + ";\tresourceId:" + resourceId + ";\ttype:" + type + ";\tobject:" + obj.getClass().toString() + "]");
                                        super.attr(ns, name, resourceId, type, obj);
                                    }
                                };
	                			
	                		}
	                		System.out.println("");
							return super.child(ns, name);
	                	}
	                };
	            }
			});

		} catch (IOException e) {
			System.out.println("failed to parse input xml file!");
			System.exit(-1);
		}

        try {
			newAndroidManifestData = aw.toByteArray();
		} catch (IOException e) {
			System.out.println("failed to generate output xml data!");
			System.exit(-1);
		}
		
        try {
			writeBytesToFile(newAndroidManifestData, new File(outputXml));
		} catch (IOException e) {
			System.out.println("failed to write output xml!");
			e.printStackTrace();
		}
        
	}

}
