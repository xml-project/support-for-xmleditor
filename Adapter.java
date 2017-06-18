/*
BSD 2-Clause License

Copyright (c) 2017, xml-project Achim Berndzen
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.xml_project.morganaxproc.oxygenadapter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ro.sync.document.DocumentPositionedInfo;
import ro.sync.exml.editor.results.iterator.ResultItem;
import ro.sync.exml.editor.xmleditor.ErrorListException;
import ro.sync.xml.transformer.xproc.api.XProcInputPort;
import ro.sync.xml.transformer.xproc.api.XProcOption;
import ro.sync.xml.transformer.xproc.api.XProcOutputPort;
import ro.sync.xml.transformer.xproc.api.XProcParameter;
import ro.sync.xml.transformer.xproc.api.XProcParametersPort;
import ro.sync.xml.transformer.xproc.api.XProcTransformerInterface;

import javax.xml.namespace.QName;
import javax.xml.transform.URIResolver;

import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

import org.xml.sax.EntityResolver;

import com.xml_project.morganaxpath.nodes.serializer.SerializerOptions;
import com.xml_project.morganaxproc.XProcCompiler;
import com.xml_project.morganaxproc.XProcCompiler.XProcCompilerException;
import com.xml_project.morganaxproc.XProcEngine;
import com.xml_project.morganaxproc.XProcInput;
import com.xml_project.morganaxproc.XProcInterfaceException;
import com.xml_project.morganaxproc.XProcOutput;
import com.xml_project.morganaxproc.XProcPipeline;
import com.xml_project.morganaxproc.XProcResult;
import com.xml_project.morganaxproc.XProcPipeline.PipelineInfo;
import com.xml_project.morganaxproc.core.XProcRuntimeException;
import com.xml_project.morganaxproc.core.port.PortSerializer;
import com.xml_project.morganaxproc.security.XProcSecurityException;
import com.xml_project.morganaxproc.XProcSource;
public class Adapter implements XProcTransformerInterface{
	
	private final List<DocumentPositionedInfo> compilationErrors = new ArrayList<DocumentPositionedInfo>();
	private String thePipelineURI = null;
	private URIResolver theURIResolver = null;
	public EntityResolver theEntityResolver = null;
	
	public List<DocumentPositionedInfo> getLastTransformationMessages() {
		return compilationErrors;
	}

	public void initialize(String pipelineURI, String uriResolverClassName, String entityResolverClassName) {
		try{
			if (uriResolverClassName != null && uriResolverClassName.trim().length()!=0)
				theURIResolver = (URIResolver) getClass().getClassLoader().loadClass(uriResolverClassName).newInstance();
			else
				theURIResolver = null;
			if (entityResolverClassName != null && entityResolverClassName.trim().length()!=0)
				theEntityResolver = (EntityResolver) getClass().getClassLoader().loadClass(entityResolverClassName).newInstance();
			else
				theEntityResolver = null;
			if (pipelineURI != null && pipelineURI.trim().length()!=0)
				thePipelineURI = pipelineURI;
			else
				thePipelineURI = null;
		}
		catch (Throwable t){
			compilationErrors.clear();
			compilationErrors.add(new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_ERROR, "Error initializing adapter: "+t.getMessage()));
			dumpStackTrace(t);
		}
	}

	public boolean supportsValidation() {return true;}

	public Map<String, List<ResultItem>> transform(XProcInputPort[] inputPorts,
												   XProcOutputPort[] outputPorts,
												   XProcOption[] options,
												   XProcParametersPort[] parameterPorts) throws Exception {
		compilationErrors.clear();
		
		if (thePipelineURI == null || thePipelineURI.trim().length()==0)
			throw new IllegalArgumentException("Internal error: The pipeline was not specified.");
		try{
			/* prepare and compile */
			XProcCompiler compiler = createCompiler();
			XProcPipeline xpl = compiler.compile(makeSource(thePipelineURI));
			PipelineInfo xplInfo = xpl.getInfo();
			
			/* set bindings */
			XProcInput input = new XProcInput();
			setInputPorts(input, xplInfo, inputPorts);
			setParameterPorts(input, xplInfo, parameterPorts);
			setOptions(input, xplInfo, options);
			
			/* now run the pipeline */
			XProcOutput output = xpl.run(input);
			if (output.wasSuccessful())
				return handleResults(output, outputPorts);
			else{
				/**
				 * Handle errors
				 */
				handleRuntimeErrors(output);
				throw new ErrorListException(compilationErrors);
			}
		}
		catch (XProcCompilerException ex){
			Elements errors = ex.getErrorDocument().getRootElement().getChildElements("error");
			for (int i=0; i < errors.size(); i++)
				compilationErrors.add(handleCompilationError(errors.get(i)));
			throw new ErrorListException(compilationErrors);
		}
		catch (XProcInterfaceException ex){
			compilationErrors.add(new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_ERROR, "Interface error: "+ex.getMessage()));
			throw new ErrorListException(compilationErrors);
		}
		catch (Throwable t){
			if (t instanceof ErrorListException)
				throw t;
			compilationErrors.add(new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_ERROR, "Internal error: "+t.getMessage()));
			dumpStackTrace(t);
			throw new ErrorListException(compilationErrors);
		}
	}

	@Override
	public List<DocumentPositionedInfo> validate() {
		final List<DocumentPositionedInfo> foundErrors = new ArrayList<DocumentPositionedInfo>();
		if (thePipelineURI == null || thePipelineURI.trim().length()==0){
			foundErrors.add(new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_ERROR,"Internal error: The pipeline was not specified."));
			return foundErrors;
		}
		final XProcCompiler compiler = createCompiler();
		
		AccessController.doPrivileged(new PrivilegedAction<Object>(){
			public Object run(){
				try{
					compiler.compile(makeSource(thePipelineURI));
				}
				catch (XProcCompilerException ex){
					Elements errors = ex.getErrorDocument().getRootElement().getChildElements("error");
					for (int i=0; i < errors.size(); i++){
						foundErrors.add(handleCompilationError(errors.get(i)));

					}
				}
				catch (Throwable t){
					foundErrors.add(new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_ERROR,t.getMessage()));
				}
				return null;
			};
		});
		return foundErrors;
	}
	/**
	 * 
	 * @return
	 */
	private XProcCompiler createCompiler(){
		return XProcEngine.newXProc().newXProcCompiler();
	}
	/**
	 * 
	 * @param t
	 */
	private void dumpStackTrace(final Throwable t){
		try{
			PrintWriter writer = new PrintWriter(new File(System.getProperty("user.home")+File.separator+"MorganaAdapterCrash.txt"));
			t.printStackTrace(writer);
			writer.flush();
			writer.close();
		}
		catch (Throwable thrown){
			/*ignore*/
		}
	}
	/**
	 * 
	 * @param uriString
	 * @return
	 * @throws Exception
	 */
	private XProcSource makeSource(final String uriString) throws Exception{
		if (theURIResolver == null)
			throw new Exception("No URIResolver found.");
		else{
			return new XProcSource(theURIResolver.resolve(uriString, null));
		}
	}
	/**
	 * 
	 * @param error
	 * @return
	 */
	private DocumentPositionedInfo handleCompilationError(Element error){
		Element position = error.getFirstChildElement("position");
		String href = position.getAttributeValue("href");
		if (href == null || href.trim().length()==0)
			href = thePipelineURI;
		StringBuilder message = new StringBuilder();
		if (error.getAttribute("code")!=null)
			message.append("err:"+error.getAttributeValue("code")+": ");
		message.append(error.getFirstChildElement("description").getValue()+" ("+error.getFirstChildElement("message").getValue()+")");
		return new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_ERROR, 
				message.toString(), href, 
				getPositionInfo(position.getAttributeValue("line")), getPositionInfo(position.getAttributeValue("column")));
	}
	/**
	 * 
	 * @param s
	 * @return
	 */
	private int getPositionInfo(String s){
		int result = DocumentPositionedInfo.NOT_KNOWN;
		if (s != null && s.trim().length()!=0){
			try{
				result = Integer.parseInt(s);
				if (result <=0)
					result = DocumentPositionedInfo.NOT_KNOWN;
			}
			catch (NumberFormatException ex){
				result = DocumentPositionedInfo.NOT_KNOWN;
			}
		}
		return result;
	}
	/**
	 * 
	 * @param theInput
	 * @param xplInfo
	 * @param parameterPorts
	 * @throws XProcInterfaceException 
	 */
	private void setParameterPorts(final XProcInput theInput, 
								   final PipelineInfo xplInfo, 
								   final XProcParametersPort[] parameterPorts) throws XProcInterfaceException{
		if (parameterPorts != null){
			for (XProcParametersPort paramBinding:parameterPorts){
				String portName = paramBinding.getPortName();
				if (paramBinding.getParameters() != null){
					boolean honor = true;

					if ("*".equals(portName)){
						portName = xplInfo.getPrimaryParameterPort();
						if (portName == null)
							throw new IllegalArgumentException("No primary parameter port declared in pipeline. Please check transformation szenario!");
						
					}
					else
						honor = xplInfo.hasDeclaredParameterPort(portName);
					if (honor){
						for (XProcParameter param:paramBinding.getParameters()){
							if (param.getValue() != null){
								String namespace = param.getNamespaceURI();
								if (namespace != null && namespace.trim().length()==0)
									namespace = null;
								theInput.setParameter(portName, param.getLocalName(), namespace, param.getValue());
							}
						}
					}
				}
			}
		}
	}
	/**
	 * 
	 * @param theInput
	 * @param xplInfo
	 * @param options
	 * @throws XProcInterfaceException
	 */
	private void setOptions(final XProcInput theInput,
							final PipelineInfo xplInfo,
							final XProcOption[] options) throws XProcInterfaceException{
		if (options != null)
			for (XProcOption option:options){
				QName optionName = null;
				if (option.getNamespaceURI()!=null)
					optionName = new QName(option.getNamespaceURI(), option.getLocalName());
				else
					optionName = new QName(option.getLocalName());
				if (xplInfo.hasDeclaredOption(optionName))
					theInput.setOption(optionName, option.getValue());
		}
	}
	/**
	 * 
	 * @param theInput
	 * @param xplInfo
	 * @param theInputPorts
	 * @throws Exception
	 */
	private void setInputPorts(final XProcInput theInput, 
							   final PipelineInfo xplInfo, 
							   final XProcInputPort[] theInputPorts) throws XProcInterfaceException, Exception{
		if (theInputPorts != null){
			for (XProcInputPort port:theInputPorts){
				if (xplInfo.hasDeclaredInputPort(port.getPortName())){
					for (String url: port.getUrls()){
						if (url != null && url.trim().length()!=0)
							theInput.addInput(port.getPortName(), makeSource(url));
					}
				}
			}
		}
	}
	private Map<String, List<ResultItem>> handleResults(final XProcOutput output, XProcOutputPort[] outputPortMappings) throws XProcRuntimeException, IOException{
		HashMap<String, List<ResultItem>> result = new HashMap<String, List<ResultItem>>();
		
		ArrayList<String> portNames = new ArrayList<String>(Arrays.asList(output.getPortNames()));
		if (outputPortMappings != null){
			for (XProcOutputPort mapping:outputPortMappings){
				String port = mapping.getPortName();
				if (portNames.contains(port)){
					portNames.remove(port);
					List<ResultItem> portResults = new ArrayList<ResultItem>();
					XProcResult xprocResult = output.getResults(port);
					String[] portDocs = serialize(xprocResult);
					
					if (mapping.getUrl()!=null && mapping.getUrl().trim().length()!=0){
						try{
							PrintWriter writer = new PrintWriter(new File(URI.create(mapping.getUrl())));
							writer.println(portDocs[portDocs.length-1]);
							writer.flush();
							writer.close();
						}
						catch (IOException ex){
							compilationErrors.add(new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_INFO, "Unable to write port '"+port+"' to '"+mapping.getUrl()+"': "+ex.getMessage()));
						}
					}
					if (mapping.showInSequenceView()){
						for (String s:portDocs)
							portResults.add(new ResultItem(ResultItem.COMMON_TYPE, s));
						result.put(port, portResults);
					}
				}
			}
		}
		/*
		 * Inspect results not mentioned in outputPortsMapping
		 */
		for (String s:portNames){
			List<ResultItem> portResults = new ArrayList<ResultItem>();
			for (String doc:serialize(output.getResults(s)))
				portResults.add(new ResultItem(ResultItem.COMMON_TYPE, doc));
			result.put(s, portResults);
		}
		
		return result;
	}
	/**
	 * 
	 * @param result
	 * @return
	 * @throws IOException 
	 * @throws XProcRuntimeException 
	 */
	private String[] serialize(XProcResult result) throws XProcRuntimeException, IOException{
		Document[] docs = result.getDocuments();
		String[] serialized = new String[docs.length];
		SerializerOptions options = new SerializerOptions();
		options.put(SerializerOptions.Options.indent, "true");
		options.put(SerializerOptions.Options.omit_xml_declaration, "true");
		PortSerializer serializer = new PortSerializer(options, null);
		for (int i=0; i < docs.length; i++)
			serialized[i] = serializer.serializeToString(null, docs[i]);
		return serialized;
	}
	/**
	 * 
	 * @param errorDocument
	 */
	private void handleRuntimeErrors(final XProcOutput output){
		Element errorRoot = output.getErrorDocument().getRootElement().getFirstChildElement("errors", XProcCompiler.XPROC_STEP_NAMESPACE);
		
		if (errorRoot== null){
			Element type = output.getErrorDocument().getRootElement().getFirstChildElement("type", XProcEngine.MORGANA_NAMESPACE);
			if (type != null && XProcSecurityException.class.getName().equals(type.getValue()))
				compilationErrors.add(new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_ERROR, output.getErrorDocument().getRootElement().getFirstChildElement("message", XProcEngine.MORGANA_NAMESPACE).getValue()+". To access resource reconfigurate security settings in MorganaXProc's configuration."));						
			else if (type != null && XProcRuntimeException.class.getName().equals(type.getValue())){
				Element errorElement = output.getErrorDocument().getRootElement().getFirstChildElement("error");
				if (errorElement == null)
					errorElement = output.getErrorDocument().getRootElement().getFirstChildElement("message");
				if (errorElement == null)
					errorElement = output.getErrorDocument().getRootElement();
				compilationErrors.add(new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_ERROR, errorElement.getValue()));
			}
			else
				compilationErrors.add(new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_ERROR, output.getErrorDocumentSerialized()));
		}
		else{
			Elements errors = errorRoot.getChildElements("error", XProcCompiler.XPROC_STEP_NAMESPACE);
			for (int i=0; i < errors.size(); i++)
				compilationErrors.add(handleRuntimeErrors(errors.get(i)));
		}
	}
	/**
	 * 
	 * @param error
	 * @return
	 */
	private DocumentPositionedInfo handleRuntimeErrors(Element error){
		String href = error.getAttributeValue("href");
		if (href == null || href.trim().length()==0)
			href = thePipelineURI;
		StringBuilder message = new StringBuilder();
		if (error.getAttribute("code") != null)
			message.append(error.getAttributeValue("code")+": ");
		if (error.getFirstChildElement("message") != null)
			message.append(error.getFirstChildElement("message").getValue());
		else
			message.append(error.getValue());
		
		return new DocumentPositionedInfo(DocumentPositionedInfo.SEVERITY_ERROR,
				message.toString(), href, 
				getPositionInfo(error.getAttributeValue("line")),
				getPositionInfo(error.getAttributeValue("column")));
	}
}
