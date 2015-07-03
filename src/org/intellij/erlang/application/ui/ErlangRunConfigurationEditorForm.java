/*
 * Copyright 2012-2014 Sergey Ignatov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.erlang.application.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModulesCombobox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.HideableTitledPanel;
import org.intellij.erlang.application.ErlangApplicationConfiguration;
import org.intellij.erlang.module.ErlangModuleType;
import org.intellij.erlang.runconfig.ui.ErlangDebuggableRunConfigurationEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ErlangRunConfigurationEditorForm extends ErlangDebuggableRunConfigurationEditor<ErlangApplicationConfiguration> {
  private JPanel component;
  private ModulesCombobox myComboModules;
  private JTextField myParamsField;
  private JTextField myModuleAndFunctionField;
  private JTextField myErlFlagsTextField;
  private JCheckBox myStopErlangInterpreterCheckBox;
  @SuppressWarnings("unused")
  private HideableTitledPanel myDebugOptionsHideablePanel;
  private TextFieldWithBrowseButton myWorkingDirectoryComponent;

  public ErlangRunConfigurationEditorForm(){
    myWorkingDirectoryComponent.addBrowseFolderListener("Choose working directory", "", null,
      FileChooserDescriptorFactory.createSingleFolderDescriptor());
  }

  @Override
  protected void doResetEditorFrom(ErlangApplicationConfiguration configuration) {
    myComboModules.fillModules(configuration.getProject(), ErlangModuleType.getInstance());
    myComboModules.setSelectedModule(configuration.getConfigurationModule().getModule());
    myParamsField.setText(configuration.getParams());
    myStopErlangInterpreterCheckBox.setSelected(configuration.stopErlang());
    myModuleAndFunctionField.setText(configuration.getModuleAndFunction());
    myErlFlagsTextField.setText(configuration.getErlFlags());
    myWorkingDirectoryComponent.setText(configuration.getWorkDirectory());
  }

  @Override
  protected void doApplyEditorTo(ErlangApplicationConfiguration configuration) throws ConfigurationException {
    configuration.setModule(myComboModules.getSelectedModule());
    configuration.setParams(myParamsField.getText());
    configuration.setModuleAndFunction(myModuleAndFunctionField.getText());
    configuration.setErlFlags(myErlFlagsTextField.getText());
    configuration.setStopErlang(myStopErlangInterpreterCheckBox.isSelected());
    configuration.setWorkDirectory(myWorkingDirectoryComponent.getText());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return component;
  }

  @Override
  protected void disposeEditor() {
    component.setVisible(false);
  }

  private void createUIComponents() {
    myDebugOptionsHideablePanel = createDebugOptionsHideablePanel();
  }
}
