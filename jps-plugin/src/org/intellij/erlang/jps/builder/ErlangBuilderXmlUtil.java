package org.intellij.erlang.jps.builder;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.io.IOException;


public class ErlangBuilderXmlUtil {
  private ErlangBuilderXmlUtil() {
  }

  @Nullable
  public static <T> T readFromXML(@NotNull String relativePath,
                                  @NotNull CompileContext context,
                                  Class<T> tClass) throws JDOMException, IOException {
    File dataStorageRoot = context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot();
    File xmlFile = new File(dataStorageRoot, relativePath);
    if (!xmlFile.exists()) return null;

    Document document = JDOMUtil.loadDocument(xmlFile);
    return XmlSerializer.deserialize(document, tClass);

  }

  public static <T> void writeToXML(@NotNull String relativePath,
                                    @NotNull T serializedObject,
                                    @NotNull CompileContext context) throws IOException {

    Document document = new Document(XmlSerializer.serialize(serializedObject, new SkipDefaultValuesSerializationFilters()));
    File dataStorageRoot = context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot();
    File depsConfigFile = new File(dataStorageRoot, relativePath);
    //noinspection ResultOfMethodCallIgnored
    depsConfigFile.getParentFile().mkdirs();
    JDOMUtil.writeDocument(document, depsConfigFile, SystemProperties.getLineSeparator());
  }
}
