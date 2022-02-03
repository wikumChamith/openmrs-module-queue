/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.queue.web.resources;

import javax.validation.constraints.NotNull;

import java.util.Optional;

import org.openmrs.api.context.Context;
import org.openmrs.module.queue.api.QueueService;
import org.openmrs.module.queue.model.Queue;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

@SuppressWarnings("unused")
@Resource(name = RestConstants.VERSION_1 + "/queue", supportedClass = Queue.class, supportedOpenmrsVersions = {
        "2.0 - 2.*" })
public class QueueResource extends DelegatingCrudResource<Queue> {
	
	private final QueueService queueService;
	
	public QueueResource() {
		this.queueService = Context.getService(QueueService.class);
	}
	
	@Override
	public Queue getByUniqueId(@NotNull String uuid) {
		Optional<Queue> optionalQueue = queueService.getQueueByUuid(uuid);
		if (!optionalQueue.isPresent()) {
			throw new ObjectNotFoundException("Could not find queue with UUID " + uuid);
		}
		return optionalQueue.get();
	}
	
	@Override
	protected void delete(Queue queue, String retireReason, RequestContext requestContext) throws ResponseException {
		if (!this.queueService.getQueueByUuid(queue.getUuid()).isPresent()) {
			throw new ObjectNotFoundException("Could not find queue with uuid " + queue.getUuid());
		}
		this.queueService.voidQueue(queue.getUuid(), retireReason);
	}
	
	@Override
	public Queue newDelegate() {
		return new Queue();
	}
	
	@Override
	public Queue save(Queue queue) {
		return this.queueService.createQueue(queue);
	}
	
	@Override
	public void purge(Queue queue, RequestContext requestContext) throws ResponseException {
		this.queueService.purgeQueue(queue);
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
		DelegatingResourceDescription resourceDescription = new DelegatingResourceDescription();
		if (representation instanceof RefRepresentation) {
			this.addSharedResourceDescriptionProperty(resourceDescription);
			resourceDescription.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
			return resourceDescription;
		} else if (representation instanceof DefaultRepresentation) {
			this.addSharedResourceDescriptionProperty(resourceDescription);
			resourceDescription.addProperty("location", Representation.REF);
			resourceDescription.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
			return resourceDescription;
		} else if (representation instanceof FullRepresentation) {
			this.addSharedResourceDescriptionProperty(resourceDescription);
			resourceDescription.addProperty("location", Representation.FULL);
			resourceDescription.addProperty("auditInfo");
			return resourceDescription;
		}
		return null;
	}
	
	private void addSharedResourceDescriptionProperty(DelegatingResourceDescription resourceDescription) {
		resourceDescription.addSelfLink();
		resourceDescription.addProperty("uuid");
		resourceDescription.addProperty("display");
		resourceDescription.addProperty("name");
		resourceDescription.addProperty("description");
	}
	
	@PropertyGetter("display")
	public String getDisplay(Queue queue) {
		return queue.getName();
	}
}
