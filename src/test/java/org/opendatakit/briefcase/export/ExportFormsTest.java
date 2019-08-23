/*
 * Copyright (C) 2018 Nafundi
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
package org.opendatakit.briefcase.export;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresent;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.opendatakit.briefcase.export.ExportConfiguration.Builder.empty;
import static org.opendatakit.briefcase.export.ExportForms.buildCustomConfPrefix;
import static org.opendatakit.briefcase.model.FormStatusBuilder.buildFormStatusList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.opendatakit.briefcase.model.BriefcasePreferences;
import org.opendatakit.briefcase.model.FormStatus;
import org.opendatakit.briefcase.model.InMemoryPreferences;

public class ExportFormsTest {
  private static ExportConfiguration VALID_CONFIGURATION;
  private static ExportConfiguration INVALID_CONFIGURATION;

  static {
    try {
      VALID_CONFIGURATION = empty()
          .setExportDir(Paths.get(Files.createTempDirectory("briefcase_test").toUri()))
          .build();
      INVALID_CONFIGURATION = empty().build();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void can_merge_an_incoming_list_of_forms() {
    ExportForms forms = new ExportForms(new ArrayList<>(), empty().build(), new HashMap<>(), new HashMap<>());
    assertThat(forms.size(), is(0));
    int expectedSize = 10;

    forms.merge(buildFormStatusList(expectedSize));

    assertThat(forms.size(), is(expectedSize));
  }

  @Test
  public void manages_a_forms_configuration() {
    ExportForms forms = new ExportForms(buildFormStatusList(2), empty().build(), new HashMap<>(), new HashMap<>());
    FormStatus firstForm = forms.get(0);
    FormStatus secondForm = forms.get(1);

    assertThat(forms.hasConfiguration(firstForm), is(false));

    forms.putConfiguration(firstForm, VALID_CONFIGURATION);

    assertThat(forms.hasConfiguration(firstForm), is(true));
    assertThat(forms.getConfiguration(firstForm.getFormId()), is(VALID_CONFIGURATION));
    assertThat(forms.getConfiguration(firstForm.getFormId()), is(VALID_CONFIGURATION));

    forms.putConfiguration(secondForm, INVALID_CONFIGURATION);
    assertThat(forms.getCustomConfigurations().values(), hasSize(2));
    assertThat(forms.getCustomConfigurations().values(), allOf(
        hasItem(VALID_CONFIGURATION),
        hasItem(INVALID_CONFIGURATION)
    ));

    forms.removeConfiguration(firstForm);

    assertThat(forms.hasConfiguration(firstForm), is(false));
  }

  @Test
  public void manages_forms_selection() {
    ExportForms forms = new ExportForms(buildFormStatusList(10), empty().build(), new HashMap<>(), new HashMap<>());
    assertThat(forms.getSelectedForms(), hasSize(0));
    assertThat(forms.allSelected(), is(false));
    assertThat(forms.noneSelected(), is(true));
    assertThat(forms.someSelected(), is(false));

    forms.get(0).setSelected(true);
    assertThat(forms.getSelectedForms(), hasSize(1));
    assertThat(forms.allSelected(), is(false));
    assertThat(forms.noneSelected(), is(false));
    assertThat(forms.someSelected(), is(true));

    forms.selectAll();
    assertThat(forms.getSelectedForms(), hasSize(10));
    assertThat(forms.allSelected(), is(true));
    assertThat(forms.noneSelected(), is(false));
    assertThat(forms.someSelected(), is(true));

    forms.clearAll();
    assertThat(forms.getSelectedForms(), hasSize(0));
    assertThat(forms.allSelected(), is(false));
    assertThat(forms.noneSelected(), is(true));
    assertThat(forms.someSelected(), is(false));
  }

  @Test
  public void knows_if_all_selected_forms_have_a_valid_configuration() {
    ExportForms forms = new ExportForms(buildFormStatusList(10), empty().build(), new HashMap<>(), new HashMap<>());
    FormStatus form = forms.get(0);
    form.setSelected(true);

    assertThat(forms.allSelectedFormsHaveConfiguration(), is(false));

    forms.putConfiguration(form, VALID_CONFIGURATION);

    assertThat(forms.allSelectedFormsHaveConfiguration(), is(true));
  }

  @Test
  public void appends_status_history_on_forms() {
    ExportForms forms = new ExportForms(buildFormStatusList(10), empty().build(), new HashMap<>(), new HashMap<>());
    FormStatus form = forms.get(0);
    ExportEvent event = ExportEvent.progress(0.33D, getFormDef(form).getFormId());
    forms.appendStatus(event);
    assertThat(form.getStatusHistory(), containsString("Exported 33% of the submissions"));
    assertThat(form.getStatusHistory().split("\n").length, is(2)); // There is a leading \n
  }

  @Test
  public void sets_the_last_export_datetime_when_appending_the_success_of_exporting_a_form() {
    ExportForms forms = new ExportForms(buildFormStatusList(10), empty().build(), new HashMap<>(), new HashMap<>());
    FormStatus form = forms.get(0);

    assertThat(forms.getLastExportDateTime(form), isEmpty());

    ExportEvent event = ExportEvent.successForm(10, getFormDef(form).getFormId());
    forms.appendStatus(event);

    assertThat(forms.getLastExportDateTime(form), isPresent());
  }

  private static FormDefinition getFormDef(FormStatus form) {
    return new FormDefinition(
        form.getFormId(),
        form.getFormFile(),
        form.getFormName(),
        form.isEncrypted(),
        null,
        Collections.emptyList()
    );
  }

  @Test
  public void it_lets_a_third_party_react_to_successful_exports() {
    AtomicInteger count = new AtomicInteger(0);
    ExportForms forms = new ExportForms(buildFormStatusList(10), empty().build(), new HashMap<>(), new HashMap<>());
    FormStatus form = forms.get(0);
    forms.onSuccessfulExport((formId, exportDateTime) -> {
      if (formId.equals(form.getFormId()))
        count.incrementAndGet();
    });

    ExportEvent event = ExportEvent.successForm(10, getFormDef(form).getFormId());
    forms.appendStatus(event);

    assertThat(count.get(), is(1));
  }

  @Test
  public void it_has_a_factory_that_creates_a_new_instance_from_saved_preferences() {
    LocalDateTime exportDateTime = LocalDateTime.now();
    List<FormStatus> formsList = buildFormStatusList(10);
    FormStatus form = formsList.get(0);
    String formId = form.getFormId();
    BriefcasePreferences exportPreferences = new BriefcasePreferences(InMemoryPreferences.empty());
    exportPreferences.putAll(VALID_CONFIGURATION.asMap(buildCustomConfPrefix(formId)));
    exportPreferences.put(ExportForms.buildExportDateTimePrefix(formId), exportDateTime.format(ISO_DATE_TIME));
    BriefcasePreferences appPreferences = new BriefcasePreferences(InMemoryPreferences.empty());

    ExportForms forms = ExportForms.load(empty().build(), formsList, exportPreferences);

    assertThat(forms.size(), is(10));
    assertThat(forms.hasConfiguration(form), is(true));
    assertThat(forms.getLastExportDateTime(form), isPresent());
  }

}
