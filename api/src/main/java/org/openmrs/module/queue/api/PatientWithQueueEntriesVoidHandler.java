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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.annotation.Handler;
import org.openmrs.api.APIException;
import org.openmrs.api.ValidationException;
import org.openmrs.api.handler.VoidHandler;
import org.openmrs.module.queue.api.search.QueueEntrySearchCriteria;
import org.openmrs.module.queue.model.QueueEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Voids all queue entries belonging to a patient when that patient is voided. Core knows nothing
 * about queue entries, so nothing in its void cascade touches them; without this handler a voided
 * patient's queue entries would remain active and keep appearing in service queues.
 * <p>
 * Note that this also fires when patients are merged, since core voids the non-preferred patient.
 * Queue entries are not reassigned to the preferred patient; they are voided with the merge reason.
 */
@Handler(supports = Patient.class)
public class PatientWithQueueEntriesVoidHandler implements VoidHandler<Patient> {
	
	private final Log log = LogFactory.getLog(getClass());
	
	private final QueueEntryService queueEntryService;
	
	@Autowired
	public PatientWithQueueEntriesVoidHandler(@Qualifier("queue.QueueEntryService") QueueEntryService queueEntryService) {
		this.queueEntryService = queueEntryService;
	}
	
	@Override
	public void handle(Patient patient, User voidingUser, Date voidedDate, String voidReason) {
		if (patient.getPatientId() == null) {
			return;
		}
		QueueEntrySearchCriteria criteria = new QueueEntrySearchCriteria();
		criteria.setPatient(patient);
		criteria.setIncludedVoided(true);
		List<QueueEntry> toVoid = new ArrayList<>();
		for (QueueEntry qe : queueEntryService.getQueueEntries(criteria)) {
			if (!qe.getVoided()) {
				qe.setVoided(true);
				qe.setVoidReason(voidReason);
				qe.setVoidedBy(voidingUser);
				qe.setDateVoided(voidedDate);
				toVoid.add(qe);
			}
		}
		if (toVoid.isEmpty()) {
			return;
		}
		log.info("Voiding " + toVoid.size() + " queue entries of patient " + patient.getPatientId() + " with reason: "
		        + voidReason);
		// Mark every entry voided before saving any of them: saving runs the queue entry validator, whose
		// duplicate check queries the database, and Hibernate flushes all pending voids before that query
		for (QueueEntry qe : toVoid) {
			try {
				queueEntryService.saveQueueEntry(qe);
			}
			catch (ValidationException e) {
				throw new APIException("Unable to void queue entry " + qe.getQueueEntryId() + " while voiding patient "
				        + patient.getPatientId() + ": " + e.getMessage(), e);
			}
			log.trace("Voided queue entry " + qe + " on " + voidedDate);
		}
	}
}
