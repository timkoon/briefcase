/*
 * Copyright (C) 2019 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.briefcase.delivery.ui.transfer.sourcetarget.source;

import static java.awt.Cursor.getPredefinedCursor;
import static org.opendatakit.briefcase.delivery.ui.reused.UI.errorMessage;
import static org.opendatakit.briefcase.delivery.ui.reused.UI.removeAllMouseListeners;
import static org.opendatakit.briefcase.delivery.ui.reused.filsystem.FileChooser.isUnderBriefcaseFolder;
import static org.opendatakit.briefcase.operations.transfer.pull.filesystem.FormInstaller.scanCollectFormsAt;

import java.awt.Cursor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JLabel;
import org.bushe.swing.event.EventBus;
import org.opendatakit.briefcase.delivery.ui.reused.filsystem.FileChooser;
import org.opendatakit.briefcase.operations.transfer.SourceOrTarget;
import org.opendatakit.briefcase.operations.transfer.TransferForms;
import org.opendatakit.briefcase.operations.transfer.pull.PullEvent;
import org.opendatakit.briefcase.operations.transfer.pull.filesystem.PathSourceOrTarget;
import org.opendatakit.briefcase.operations.transfer.pull.filesystem.PullFromCollectDir;
import org.opendatakit.briefcase.reused.Container;
import org.opendatakit.briefcase.reused.job.JobsRunner;
import org.opendatakit.briefcase.reused.model.form.FormMetadata;

/**
 * Represents a filesystem location pointing to Collect's form directory as a source of forms for the Pull UI Panel.
 */
public class CollectDir implements SourcePanelValueContainer {
  private final Container container;
  private final Consumer<SourcePanelValueContainer> consumer;
  private PathSourceOrTarget value;

  CollectDir(Container container, Consumer<SourcePanelValueContainer> consumer) {
    this.container = container;
    this.consumer = consumer;
  }

  private static boolean isValidCustomDir(Path f) {
    return Files.exists(f) && Files.isDirectory(f) && !isUnderBriefcaseFolder(f.toFile()) && Files.exists(f.resolve("forms")) && Files.isDirectory(f.resolve("forms"));
  }

  @Override
  public List<FormMetadata> getFormList() {
    return scanCollectFormsAt(value.getPath());
  }

  @Override
  public JobsRunner pull(TransferForms forms, boolean startFromLast) {
    PullFromCollectDir pullJob = new PullFromCollectDir(container, EventBus::publish);
    return JobsRunner.launchAsync(forms.map(formMetadata -> pullJob.pull(
        formMetadata,
        formMetadata.withFormFile(container.workspace.buildFormFile(formMetadata))
    ))).onComplete(() -> EventBus.publish(new PullEvent.PullComplete()));
  }

  @Override
  public String getDescription() {
    return value.toString();
  }

  @Override
  public void onSelect(java.awt.Container container) {
    FileChooser
        .directory(container, Optional.empty())
        .choose()
        // TODO Changing the FileChooser to handle Paths instead of Files would improve this code and it's also coherent with the modernization (use NIO2 API) of this basecode
        .ifPresent(file -> {
          if (isValidCustomDir(file.toPath()))
            set(PathSourceOrTarget.collectFormAt(file.toPath()));
          else {
            errorMessage(
                "Wrong directory",
                "The selected directory doesn't look like an ODK Collect storage directory. Please select another directory."
            );
          }
        });
  }

  @Override
  public void decorate(JLabel label) {
    label.setText(getDescription());
    label.setCursor(getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    removeAllMouseListeners(label);
  }

  @Override
  public boolean canBeReloaded() {
    return false;
  }

  @Override
  public SourceOrTarget.Type getType() {
    return SourceOrTarget.Type.COLLECT_DIRECTORY;
  }

  @Override
  public void set(SourceOrTarget value) {
    this.value = (PathSourceOrTarget) value;
    consumer.accept(this);
  }

  @Override
  public SourceOrTarget get() {
    return value;
  }

  @Override
  public String toString() {
    return "Collect directory";
  }
}
