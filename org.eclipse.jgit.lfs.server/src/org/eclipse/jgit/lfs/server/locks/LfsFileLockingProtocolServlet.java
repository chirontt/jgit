/*
 * Copyright (C) 2021, Tue Ton <chirontt@gmail.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.server.locks;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lfs.errors.LfsUnavailable;
import org.eclipse.jgit.lfs.server.LfsProtocolServlet;
import org.eclipse.jgit.lfs.server.LfsRef;
import org.eclipse.jgit.lfs.server.LfsUtils;
import org.eclipse.jgit.lfs.server.RepositoryAccessor;
import org.eclipse.jgit.lfs.server.internal.LfsGson;
import org.eclipse.jgit.lfs.server.locks.LfsFileLockingRequest.CreateLock;
import org.eclipse.jgit.lfs.server.locks.LfsFileLockingRequest.DeleteLock;
import org.eclipse.jgit.lfs.server.locks.LfsFileLockingRequest.ListLocksToVerify;
import org.eclipse.jgit.lfs.server.locks.LfsFileLockingRequest.LockAction;
import org.eclipse.jgit.lfs.server.locks.LfsFileLockingResponse.CreatedOrDeletedLock;
import org.eclipse.jgit.lfs.server.locks.LfsFileLockingResponse.Error;
import org.eclipse.jgit.lfs.server.locks.LfsFileLockingResponse.LockExistsError;
import org.eclipse.jgit.lfs.server.locks.LfsFileLockingResponse.LocksToVerify;
import org.eclipse.jgit.lfs.server.locks.errors.LfsLockExists;
import org.eclipse.jgit.lfs.server.locks.errors.LfsLockUnauthorized;
import org.eclipse.jgit.lfs.server.locks.internal.LfsFileLockingText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LFS file locking handler implementing the LFS File Locking API [1]
 *
 * [1] https://github.com/git-lfs/git-lfs/blob/main/docs/api/locking.md
 */
public abstract class LfsFileLockingProtocolServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory
			.getLogger(LfsFileLockingProtocolServlet.class);

	private static final String CONTENTTYPE_VND_GIT_LFS_JSON = LfsProtocolServlet.CONTENTTYPE_VND_GIT_LFS_JSON;

	/**
	 * Get the lock manager which handles the LFS locks.
	 *
	 * @return the lock manager
	 * @throws org.eclipse.jgit.lfs.errors.LfsException
	 *             implementations should throw more specific exceptions to
	 *             signal which type of error occurred:
	 *             <dl>
	 *             <dt>{@link org.eclipse.jgit.lfs.server.locks.errors.LfsLockExists}</dt>
	 *             <dd>when the lock already exists</dd>
	 *             <dt>{@link org.eclipse.jgit.lfs.server.locks.errors.LfsLockUnauthorized}</dt>
	 *             <dd>when the lock operation is not allowed</dd>
	 *             <dt>{@link org.eclipse.jgit.lfs.errors.LfsRepositoryNotFound}</dt>
	 *             <dd>when the repository does not exist for the user</dd>
	 *             <dt>{@link org.eclipse.jgit.lfs.errors.LfsRepositoryReadOnly}</dt>
	 *             <dd>when the user has read, but not write access.</dd>
	 *             <dt>{@link org.eclipse.jgit.lfs.errors.LfsUnavailable}</dt>
	 *             <dd>when LFS file locking is not available</dd>
	 *             <dt>{@link org.eclipse.jgit.lfs.errors.LfsException}</dt>
	 *             <dd>when an unexpected internal server error occurred</dd>
	 *             </dl>
	 */
	protected abstract LockManager getLockManager() throws LfsException;

	/**
	 * Get the repository accessor for the given path.
	 *
	 * @param path
	 *            the path
	 * @return the accessor for determining read/write access to the main git
	 *         repository
	 * @throws org.eclipse.jgit.lfs.errors.LfsException
	 *             implementations should throw more specific exceptions to
	 *             signal which type of error occurred:
	 *             <dl>
	 *             <dt>{@link org.eclipse.jgit.lfs.errors.LfsRepositoryNotFound}</dt>
	 *             <dd>when the repository does not exist for the user</dd>
	 *             <dt>{@link org.eclipse.jgit.lfs.errors.LfsRepositoryReadOnly}</dt>
	 *             <dd>when the user has read, but not write access.</dd>
	 *             <dt>{@link org.eclipse.jgit.lfs.errors.LfsUnavailable}</dt>
	 *             <dd>when LFS file locking is not available</dd>
	 *             <dt>{@link org.eclipse.jgit.lfs.errors.LfsException}</dt>
	 *             <dd>when an unexpected internal server error occurred</dd>
	 *             </dl>
	 */
	protected abstract RepositoryAccessor getRepositoryAccessor(String path)
			throws LfsException;

	/**
	 *
	 * @param action
	 * @param refName
	 * @param username
	 * @throws LfsException
	 */
	protected void checkAccessToMainRepository(LockAction action,
			String refName, String username) throws LfsException {
		RepositoryAccessor repoAccessor = getRepositoryAccessor(null);
		if (repoAccessor == null) {
			return;
		}

		if (action == LockAction.LIST_LOCKS) {
			repoAccessor.checkReadAccess(refName, username);
		} else {
			repoAccessor.checkWriteAccess(refName, username);
		}
	}

	/** {@inheritDoc} */
	@SuppressWarnings("boxing")
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		LOG.debug("GET headers: " + getHeaders(req)); //$NON-NLS-1$
		String pathInfo = req.getPathInfo();
		Map<String, String[]> params = req.getParameterMap();
		LOG.debug("Locks query string: " + req.getQueryString()); //$NON-NLS-1$

		Writer w = new BufferedWriter(
				new OutputStreamWriter(resp.getOutputStream(), UTF_8));
		String path = getQueryParameterValue(params, "path"); //$NON-NLS-1$
		String id = getQueryParameterValue(params, "id"); //$NON-NLS-1$
		String cursor = getQueryParameterValue(params, "cursor"); //$NON-NLS-1$
		String limitStr = getQueryParameterValue(params, "limit"); //$NON-NLS-1$
		String refspec = getQueryParameterValue(params, "refspec"); //$NON-NLS-1$

		resp.setContentType(CONTENTTYPE_VND_GIT_LFS_JSON);
		try {
			if (pathInfo != null) {
				throw new LfsException(
						"Invalid path info in the GET request: " + pathInfo); //$NON-NLS-1$
			}
			int limit = 0; // default is no limit
			if (limitStr != null && !limitStr.isEmpty()) {
				try {
					limit = Integer.parseInt(limitStr);
				} catch (NumberFormatException e) {
					throw new LfsException(
							"Invalid limit parameter in the GET request: " //$NON-NLS-1$
									+ limitStr);
				}
			}
			checkAccessToMainRepository(
					LfsFileLockingRequest.LockAction.LIST_LOCKS, refspec,
					LfsUtils.getUsername(req));
			LockManager lockManager = getLockManager();
			if (lockManager == null) {
				throw new LfsUnavailable(
						LfsFileLockingText.get().fileLockingServiceUnavailable);
			}
			LOG.debug(String.format(
					"Retrieving locks, with path=%1$s, id=%2$s, cursor=%3$s, limit=%4$d, refspec=%5$s", //$NON-NLS-1$
					path, id, cursor, limit, refspec));
			LfsFileLockingResponse.Locks locks = lockManager.listLocks(path, id,
					cursor, limit, refspec);
			resp.setStatus(SC_OK);
			LfsGson.toJson(locks, w);
		} catch (LfsLockUnauthorized e) {
			sendError(resp, w, SC_FORBIDDEN, e.getMessage());
		} catch (LfsUnavailable e) {
			sendError(resp, w, SC_NOT_FOUND, e.getMessage());
		} catch (LfsException e) {
			sendError(resp, w, SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} finally {
			w.flush();
		}
	}

	/** {@inheritDoc} */
	@SuppressWarnings("boxing")
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		LOG.debug("POST headers: " + getHeaders(req)); //$NON-NLS-1$
		Writer w = new BufferedWriter(
				new OutputStreamWriter(resp.getOutputStream(), UTF_8));

		Reader r = new BufferedReader(
				new InputStreamReader(req.getInputStream(), UTF_8));
		String pathInfo = req.getPathInfo();
		LOG.debug("Path info: " + pathInfo); //$NON-NLS-1$

		resp.setContentType(CONTENTTYPE_VND_GIT_LFS_JSON);
		try {
			String username = LfsUtils.getUsername(req);
			if (pathInfo == null) {
				// create-lock request;
				CreateLock createLock = LfsGson.fromJson(r, CreateLock.class);
				LfsRef ref = createLock.getRef();
				String refName = ref == null ? null : ref.getName();
				checkAccessToMainRepository(LockAction.CREATE_LOCK, refName,
						username);
				LockManager lockManager = getLockManager();
				if (lockManager == null) {
					throw new LfsUnavailable(LfsFileLockingText
							.get().fileLockingServiceUnavailable);
				}
				LOG.debug(String.format(
						"Creating lock, with path=%1$s, refspec=%2$s, username=%3$s", //$NON-NLS-1$
						createLock.getPath(), refName, username));
				CreatedOrDeletedLock lockCreated = lockManager
						.createLock(createLock.getPath(), refName, username);
				resp.setStatus(SC_CREATED);
				LfsGson.toJson(lockCreated, w);
			} else if (pathInfo.equals("/verify")) { //$NON-NLS-1$
				// list-locks-to-verify request;
				ListLocksToVerify listLocksToVerify = LfsGson.fromJson(r,
						ListLocksToVerify.class);
				LfsRef ref = listLocksToVerify.getRef();
				String refName = ref == null ? null : ref.getName();
				checkAccessToMainRepository(LockAction.LIST_LOCKS_TO_VERIFY,
						refName, username);
				LockManager lockManager = getLockManager();
				if (lockManager == null) {
					throw new LfsUnavailable(LfsFileLockingText
							.get().fileLockingServiceUnavailable);
				}
				LOG.debug(String.format(
						"Retrieving locks for verification, with cursor=%1$s, limit=%2$d, refspec=%3$s, username=%4$s", //$NON-NLS-1$
						listLocksToVerify.getCursor(),
						listLocksToVerify.getLimit(), refName, username));
				LocksToVerify locksToVerify = lockManager.listLocksToVerify(
						refName, username, listLocksToVerify.getCursor(),
						listLocksToVerify.getLimit());
				resp.setStatus(SC_OK);
				LfsGson.toJson(locksToVerify, w);
			} else {
				// delete-lock request, with path info in the form of
				// "/:id/unlock"
				String lockId = null;
				String[] tokens = pathInfo.split("/"); //$NON-NLS-1$
				if (tokens.length != 3 || !tokens[0].isEmpty()
						|| !tokens[2].equals("unlock")) { //$NON-NLS-1$
					String error = MessageFormat.format(
							LfsFileLockingText.get().invalidDeleteLockEndpoint,
							pathInfo);
					throw new LfsException(error);
				}
				lockId = tokens[1];
				DeleteLock deleteLock = LfsGson.fromJson(r, DeleteLock.class);
				LfsRef ref = deleteLock.getRef();
				String refName = ref == null ? null : ref.getName();
				checkAccessToMainRepository(LockAction.DELETE_LOCK, refName,
						username);
				LockManager lockManager = getLockManager();
				if (lockManager == null) {
					throw new LfsUnavailable(LfsFileLockingText
							.get().fileLockingServiceUnavailable);
				}
				boolean isAdministrator = lockManager
						.isLockAdministrator(username);
				LOG.debug(String.format(
						"Deleting lock, with id=%1$s, ref=%2$s, username=%3$s, force=%4$b", //$NON-NLS-1$
						lockId, refName, username,
						deleteLock.isForce() && isAdministrator));
				CreatedOrDeletedLock lockDeleted = lockManager.deleteLock(
						lockId, refName, username,
						deleteLock.isForce() && isAdministrator);
				resp.setStatus(SC_OK);
				LfsGson.toJson(lockDeleted, w);
			}
		} catch (LfsLockUnauthorized e) {
			sendError(resp, w, SC_FORBIDDEN, e.getMessage());
		} catch (LfsUnavailable e) {
			sendError(resp, w, SC_NOT_FOUND, e.getMessage());
		} catch (LfsLockExists e) {
			resp.setStatus(SC_CONFLICT);
			Error error = new LockExistsError(e.getMessage(), e.getLock());
			LfsGson.toJson(error, w);
		} catch (LfsException e) {
			sendError(resp, w, SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} finally {
			w.flush();
		}
	}

	private String getQueryParameterValue(Map<String, String[]> params,
			String key) {
		String[] values = params.getOrDefault(key, null);
		if (values == null || values.length == 0) {
			return null;
		}
		return URLDecoder.decode(values[0], StandardCharsets.UTF_8);
	}

	private void sendError(HttpServletResponse resp, Writer writer, int status,
			String message) {
		resp.setStatus(status);
		Error error = new Error(message);
		LfsGson.toJson(error, writer);
	}

	private String getHeaders(HttpServletRequest req) {
		StringBuilder builder = new StringBuilder();
		Enumeration<String> headers = req.getHeaderNames();
		while (headers.hasMoreElements()) {
			String headerName = headers.nextElement();
			builder.append(headerName + "="); //$NON-NLS-1$
			Enumeration<String> headerValues = req.getHeaders(headerName);
			List<String> valueList = new ArrayList<>();
			while (headerValues.hasMoreElements()) {
				valueList.add(headerValues.nextElement());
			}
			builder.append(valueList.toString() + ", "); //$NON-NLS-1$
		}
		return builder.toString();
	}

}
