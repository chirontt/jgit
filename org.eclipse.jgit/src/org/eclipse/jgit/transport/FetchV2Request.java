/*
 * Copyright (C) 2018, Google LLC.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.transport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Fetch request from git protocol v2.
 *
 * <p>
 * This is used as an input to {@link ProtocolV2Hook}.
 *
 * @since 5.1
 */
public final class FetchV2Request {
	private final List<ObjectId> peerHas;

	private final TreeMap<String, ObjectId> wantedRefs;

	private final Set<ObjectId> wantIds;

	private final Set<ObjectId> clientShallowCommits;

	private final int deepenSince;

	private final List<String> deepenNotRefs;

	private final int depth;

	private final long filterBlobLimit;

	private final Set<String> clientCapabilities;

	private final boolean doneReceived;

	private FetchV2Request(List<ObjectId> peerHas,
			TreeMap<String, ObjectId> wantedRefs, Set<ObjectId> wantIds,
			Set<ObjectId> clientShallowCommits, int deepenSince,
			List<String> deepenNotRefs, int depth, long filterBlobLimit,
			boolean doneReceived, Set<String> clientCapabilities) {
		this.peerHas = peerHas;
		this.wantedRefs = wantedRefs;
		this.wantIds = wantIds;
		this.clientShallowCommits = clientShallowCommits;
		this.deepenSince = deepenSince;
		this.deepenNotRefs = deepenNotRefs;
		this.depth = depth;
		this.filterBlobLimit = filterBlobLimit;
		this.doneReceived = doneReceived;
		this.clientCapabilities = clientCapabilities;
	}

	/**
	 * @return object ids received in the "have" lines
	 */
	@NonNull
	List<ObjectId> getPeerHas() {
		return peerHas;
	}

	/**
	 * @return list of references received in "want-ref" lines
	 */
	@NonNull
	Map<String, ObjectId> getWantedRefs() {
		return this.wantedRefs;
	}

	/**
	 * @return object ids received in the "want" and "want-ref" lines
	 */
	@NonNull
	Set<ObjectId> getWantIds() {
		return wantIds;
	}

	/**
	 * Shallow commits the client already has.
	 *
	 * These are sent by the client in "shallow" request lines.
	 *
	 * @return set of commits the client has declared as shallow.
	 */
	@NonNull
	Set<ObjectId> getClientShallowCommits() {
		return clientShallowCommits;
	}

	/**
	 * The value in a "deepen-since" line in the request, indicating the
	 * timestamp where to stop fetching/cloning.
	 *
	 * @return timestamp in seconds since the epoch, where to stop the shallow
	 *         fetch/clone. Defaults to 0 if not set in the request.
	 */
	int getDeepenSince() {
		return deepenSince;
	}

	/**
	 * @return refs received in "deepen-not" lines.
	 */
	@NonNull
	List<String> getDeepenNotRefs() {
		return deepenNotRefs;
	}

	/**
	 * @return the depth set in a "deepen" line. 0 by default.
	 */
	int getDepth() {
		return depth;
	}

	/**
	 * @return the blob limit set in a "filter" line (-1 if not set)
	 */
	long getFilterBlobLimit() {
		return filterBlobLimit;
	}

	/**
	 * @return true if the request had a "done" line
	 */
	boolean wasDoneReceived() {
		return doneReceived;
	}

	/**
	 * Options that tune the expected response from the server, like
	 * "thin-pack", "no-progress" or "ofs-delta"
	 *
	 * These are options listed and well-defined in the git protocol
	 * specification
	 *
	 * @return options found in the request lines
	 */
	@NonNull
	Set<String> getClientCapabilities() {
		return clientCapabilities;
	}

	/** @return A builder of {@link FetchV2Request}. */
	static Builder builder() {
		return new Builder();
	}


	/** A builder for {@link FetchV2Request}. */
	static final class Builder {
		List<ObjectId> peerHas = new ArrayList<>();

		TreeMap<String, ObjectId> wantedRefs = new TreeMap<>();

		Set<ObjectId> wantIds = new HashSet<>();

		Set<ObjectId> clientShallowCommits = new HashSet<>();

		List<String> deepenNotRefs = new ArrayList<>();

		Set<String> clientCapabilities = new HashSet<>();

		int depth;

		int deepenSince;

		long filterBlobLimit = -1;

		boolean doneReceived;

		private Builder() {
		}

		/**
		 * @param objectId
		 *            object id received in a "have" line
		 * @return this builder
		 */
		Builder addPeerHas(ObjectId objectId) {
			peerHas.add(objectId);
			return this;
		}

		/**
		 * Ref received in "want-ref" line and the object-id it refers to
		 *
		 * @param refName
		 *            reference name
		 * @param oid
		 *            object id the reference is pointing at
		 * @return this builder
		 */
		Builder addWantedRef(String refName, ObjectId oid) {
			wantedRefs.put(refName, oid);
			return this;
		}

		/**
		 * @param clientCapability
		 *            capability line sent by the client
		 * @return this builder
		 */
		Builder addClientCapability(String clientCapability) {
			clientCapabilities.add(clientCapability);
			return this;
		}

		/**
		 * @param wantId
		 *            object id received in a "want" line
		 * @return this builder
		 */
		Builder addWantId(ObjectId wantId) {
			wantIds.add(wantId);
			return this;
		}

		/**
		 * @param shallowOid
		 *            object id received in a "shallow" line
		 * @return this builder
		 */
		Builder addClientShallowCommit(ObjectId shallowOid) {
			clientShallowCommits.add(shallowOid);
			return this;
		}

		/**
		 * @param d
		 *            Depth received in a "deepen" line
		 * @return this builder
		 */
		Builder setDepth(int d) {
			depth = d;
			return this;
		}

		/**
		 * @return depth set in the request (via a "deepen" line). Defaulting to
		 *         0 if not set.
		 */
		int getDepth() {
			return depth;
		}

		/**
		 * @return true if there has been at least one "deepen not" line in the
		 *         request so far
		 */
		boolean hasDeepenNotRefs() {
			return !deepenNotRefs.isEmpty();
		}

		/**
		 * @param deepenNotRef
		 *            reference received in a "deepen not" line
		 * @return this builder
		 */
		Builder addDeepenNotRef(String deepenNotRef) {
			deepenNotRefs.add(deepenNotRef);
			return this;
		}

		/**
		 * @param value
		 *            Unix timestamp received in a "deepen since" line
		 * @return this builder
		 */
		Builder setDeepenSince(int value) {
			deepenSince = value;
			return this;
		}

		/**
		 * @return shallow since value, sent before in a "deepen since" line. 0
		 *         by default.
		 */
		int getDeepenSince() {
			return deepenSince;
		}

		/**
		 * @param filterBlobLim
		 *            set in a "filter" line
		 * @return this builder
		 */
		Builder setFilterBlobLimit(long filterBlobLim) {
			filterBlobLimit = filterBlobLim;
			return this;
		}

		/**
		 * Mark that the "done" line has been received.
		 *
		 * @return this builder
		 */
		Builder setDoneReceived() {
			doneReceived = true;
			return this;
		}

		/**
		 * @return Initialized fetch request
		 */
		FetchV2Request build() {
			return new FetchV2Request(peerHas, wantedRefs, wantIds,
					clientShallowCommits, deepenSince, deepenNotRefs,
					depth, filterBlobLimit, doneReceived, clientCapabilities);
		}
	}
}