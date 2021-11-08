package org.eclipse.jgit.lfs.server;

import static org.eclipse.jgit.util.HttpSupport.HDR_AUTHORIZATION;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lfs.errors.LfsException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Misc utilities.
 *
 */
public class LfsUtils {

	private LfsUtils() {
	}

	/**
	 * Check if the given path for the refSpec exists in the repository.
	 *
	 * @param repo
	 *            The repository to search
	 * @param refSpec
	 *            The ref the path belongs to
	 * @param path
	 *            The path to check for existence
	 *
	 * @return true - if the path exists, false - otherwise
	 *
	 * @throws IOException
	 */
	public static boolean isPathPresentForRef(Repository repo, String refSpec,
			String path) throws IOException {
		// Resolve the revision specification
		ObjectId id;
		if (refSpec == null || refSpec.isEmpty()) {
			id = repo.resolve(Constants.HEAD);
		} else {
			id = repo.resolve(refSpec);
		}
		try (ObjectReader reader = repo.newObjectReader();
				RevWalk walk = new RevWalk(reader)) {
			// Get the commit object for that revision
			RevCommit commit = walk.parseCommit(id);
			// Get the revision's file tree
			// and narrow it down to the single file's path
			try (TreeWalk treewalk = TreeWalk.forPath(reader, path,
					commit.getTree())) {
				if (treewalk != null) {
					return true;
				}
				return false;
			}
		}
	}

	/**
	 * Get the username from the http request.
	 *
	 * @param req
	 *            the http request
	 *
	 * @return the username (could be null)
	 *
	 * @throws LfsException
	 *             if any errors occur
	 */
	public static String getUsername(HttpServletRequest req)
			throws LfsException {
		String username = req.getRemoteUser();
		if (username == null) {
			username = retrieveUserName(req.getHeader(HDR_AUTHORIZATION));
		}
		return username;
	}

	private static String retrieveUserName(String auth) throws LfsException {
		if (auth == null || auth.isEmpty()) {
			return null;
		}

		String[] authScheme = auth.trim().split(" "); //$NON-NLS-1$
		if (!"Basic".equalsIgnoreCase(authScheme[0])) { //$NON-NLS-1$
			throw new LfsException(
					"Only 'Basic' authentication is allowed, not " //$NON-NLS-1$
							+ authScheme[0]);
		}
		try {
			byte[] decodedBytes = Base64.getDecoder()
					.decode(authScheme[1].getBytes(StandardCharsets.UTF_8));
			String decodedString = new String(decodedBytes);
			return decodedString.split(":")[0]; //$NON-NLS-1$
		} catch (Exception e) {
			throw new LfsException(
					"Not in valid Base64 scheme: " + authScheme[1]); //$NON-NLS-1$
		}
	}

}
