package com.itranswarp.shici.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject.Kind;

/**
 * In-memory compile Java source code as String.
 * 
 * @author michael
 */
public class StringCompiler {

	@SuppressWarnings("unchecked")
	public <T> Class<T> compile(String fullClassName, String source) {
		int n = fullClassName.lastIndexOf(".");
		if (n == (-1)) {
			throw new IllegalArgumentException("Bad class name: " + fullClassName);
		}
		String className = fullClassName.substring(n + 1);
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		StandardJavaFileManager stdFileManager = compiler.getStandardFileManager(null, null, null);
		JavaFileObject input = new StringInputJavaFileObject(className, source);
		StringJavaFileManager fileManager = new StringJavaFileManager(stdFileManager);
		CompilationTask task = compiler.getTask(null, fileManager, null, null, null, Arrays.asList(input));
		Boolean result = task.call();
		if (result == null || !result.booleanValue()) {
			throw new RuntimeException("Compilation failed.");
		}
		StringOutputJavaFileObject output = fileManager.output;
		try {
			return (Class<T>) new CompiledClassLoader(output.getByteCode()).loadClass(fullClassName);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}

class StringInputJavaFileObject extends SimpleJavaFileObject {
	/**
	 * The source code of this "file".
	 */
	final String code;

	StringInputJavaFileObject(String name, String code) {
		super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
		this.code = code;
	}

	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
		return code;
	}
}

class StringOutputJavaFileObject extends SimpleJavaFileObject {

	ByteArrayOutputStream byteCode;

	StringOutputJavaFileObject(final String name, final Kind kind) {
		super(URI.create(name), kind);
	}

	@Override
	public InputStream openInputStream() {
		return new ByteArrayInputStream(getByteCode());
	}

	@Override
	public OutputStream openOutputStream() {
		byteCode = new ByteArrayOutputStream();
		return byteCode;
	}

	/**
	 * @return the byte code generated by the compiler
	 */
	byte[] getByteCode() {
		return byteCode.toByteArray();
	}
}

class StringJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {

	StringOutputJavaFileObject output;

	public StringJavaFileManager(JavaFileManager fileManager) {
		super(fileManager);
	}

	@Override
	public JavaFileObject getJavaFileForOutput(Location location, String qualifiedName, Kind kind,
			FileObject outputFile) throws IOException {
		output = new StringOutputJavaFileObject(qualifiedName, kind);
		return output;
	}
}

class CompiledClassLoader extends ClassLoader {
	final byte[] classData;

	CompiledClassLoader(byte[] classData) {
		super(CompiledClassLoader.class.getClassLoader());
		this.classData = classData;
	}

	@Override
	protected Class<?> findClass(String qualifiedClassName) throws ClassNotFoundException {
		return defineClass(qualifiedClassName, classData, 0, classData.length);
	}
}