/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.queue.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.queue.SpringTestConfiguration;
import org.openmrs.module.queue.api.search.QueueEntrySearchCriteria;
import org.openmrs.module.queue.model.QueueEntry;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = SpringTestConfiguration.class, inheritLocations = false)
public class PatientWithQueueEntriesVoidHandlerTest extends BaseModuleContextSensitiveTest {
	
	private static final List<String> INITIAL_DATASET_XML = Arrays.asList(
	    "org/openmrs/module/queue/api/dao/QueueDaoTest_locationInitialDataset.xml",
	    "org/openmrs/module/queue/api/dao/QueueEntryDaoTest_conceptsInitialDataset.xml",
	    "org/openmrs/module/queue/api/dao/QueueEntryDaoTest_patientInitialDataset.xml",
	    "org/openmrs/module/queue/api/dao/VisitQueueEntryDaoTest_visitInitialDataset.xml",
	    "org/openmrs/module/queue/api/dao/QueueDaoTest_initialDataset.xml",
	    "org/openmrs/module/queue/api/dao/QueueEntryDaoTest_initialDataset.xml",
	    "org/openmrs/module/queue/validators/QueueEntryValidatorTest_globalPropertyInitialDataset.xml");
	
	private static final String OVERLAPPING_ENTRIES_DATASET_XML = "org/openmrs/module/queue/api/dao/QueueEntryDaoTest_overlappingEntriesInitialDataset.xml";
	
	// Patient 100 owns queue entries 1 (ended), 2 (active), 3 (active) and 10 (already voided)
	private static final int PATIENT_ID = 100;
	
	// Patient 2 owns queue entry 4 and must be unaffected
	private static final int OTHER_PATIENT_ID = 2;
	
	@Autowired
	@Qualifier("queue.QueueEntryService")
	private QueueEntryService queueEntryService;
	
	@Autowired
	private PatientService patientService;
	
	private Patient patient;
	
	@BeforeEach
	public void setup() {
		INITIAL_DATASET_XML.forEach(this::executeDataSet);
		patient = patientService.getPatient(PATIENT_ID);
	}
	
	@Test
	public void shouldVoidQueueEntriesWhenPatientIsVoided() {
		QueueEntrySearchCriteria activeForPatient = new QueueEntrySearchCriteria();
		activeForPatient.setPatient(patient);
		activeForPatient.setIsEnded(false);
		assertThat(queueEntryService.getQueueEntries(activeForPatient), hasSize(2));
		
		String voidReason = "for testing";
		patient = patientService.voidPatient(patient, voidReason);
		assertThat(patient.getVoided(), is(true));
		assertThat(patient.getDateVoided(), notNullValue());
		
		patient = patientService.getPatient(PATIENT_ID);
		
		for (int id : new int[] { 1, 2, 3 }) {
			QueueEntry qe = queueEntryService.getQueueEntryById(id).get();
			assertThat("queue entry " + id + " should be voided", qe.getVoided(), is(true));
			assertThat(qe.getVoidReason(), equalTo(voidReason));
			assertThat(qe.getDateVoided().getTime(), equalTo(patient.getDateVoided().getTime()));
			assertThat(qe.getVoidedBy(), equalTo(patient.getVoidedBy()));
		}
		
		activeForPatient.setIncludedVoided(true);
		List<QueueEntry> remaining = queueEntryService.getQueueEntries(activeForPatient);
		assertThat(remaining.stream().map(QueueEntry::getId).collect(Collectors.toList()), containsInAnyOrder(2, 3, 10));
		assertThat(remaining.stream().allMatch(QueueEntry::getVoided), is(true));
	}
	
	@Test
	public void shouldVoidOverlappingQueueEntriesInTheSameQueue() {
		executeDataSet(OVERLAPPING_ENTRIES_DATASET_XML);
		
		patient = patientService.voidPatient(patient, "for testing");
		
		assertThat(queueEntryService.getQueueEntryById(3).get().getVoided(), is(true));
		assertThat(queueEntryService.getQueueEntryById(12).get().getVoided(), is(true));
		assertThat(patientService.getPatient(PATIENT_ID).getVoided(), is(true));
	}
	
	@Test
	public void shouldNotVoidQueueEntriesOfOtherPatients() {
		patientService.voidPatient(patient, "for testing");
		QueueEntry other = queueEntryService.getQueueEntryById(4).get();
		assertThat(other.getPatient().getPatientId(), equalTo(OTHER_PATIENT_ID));
		assertThat(other.getVoided(), is(false));
	}
	
	@Test
	public void shouldNotChangeAlreadyVoidedQueueEntries() {
		QueueEntry alreadyVoided = queueEntryService.getQueueEntryById(10).get();
		assertThat(alreadyVoided.getVoided(), is(true));
		Date originalDateVoided = alreadyVoided.getDateVoided();
		String originalVoidReason = alreadyVoided.getVoidReason();
		
		patientService.voidPatient(patient, "for testing");
		
		alreadyVoided = queueEntryService.getQueueEntryById(10).get();
		assertThat(alreadyVoided.getVoided(), is(true));
		assertThat(alreadyVoided.getDateVoided(), equalTo(originalDateVoided));
		assertThat(alreadyVoided.getVoidReason(), equalTo(originalVoidReason));
	}
	
	@Test
	public void shouldDoNothingForPatientWithoutQueueEntries() {
		// Patient 6 comes from the standard test dataset and has no queue entries
		Patient noEntries = patientService.getPatient(6);
		QueueEntrySearchCriteria criteria = new QueueEntrySearchCriteria();
		criteria.setPatient(noEntries);
		criteria.setIncludedVoided(true);
		assertThat(queueEntryService.getQueueEntries(criteria), hasSize(0));
		
		Patient voided = patientService.voidPatient(noEntries, "for testing");
		
		assertThat(voided.getVoided(), is(true));
		assertThat(queueEntryService.getQueueEntries(criteria), hasSize(0));
	}
	
	@Test
	public void shouldVoidQueueEntriesWhenPatientIsMergedIntoAnother() throws Exception {
		// Core moves the non-preferred patient's visits to the preferred patient, then voids the non-preferred one
		Patient preferred = patientService.getPatient(OTHER_PATIENT_ID);
		patientService.mergePatients(preferred, patient);
		
		Context.flushSession();
		Context.clearSession();
		
		assertThat(patientService.getPatient(PATIENT_ID).getVoided(), is(true));
		for (int id : new int[] { 1, 2, 3 }) {
			QueueEntry qe = queueEntryService.getQueueEntryById(id).get();
			assertThat("queue entry " + id + " should be voided", qe.getVoided(), is(true));
			assertThat(qe.getVoidReason(), equalTo("Merged with patient #" + OTHER_PATIENT_ID));
		}
	}
}
