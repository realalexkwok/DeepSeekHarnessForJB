package io.dsh.jb.util

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Filesystem helpers that stay symlink-safe. Pure JVM (no IntelliJ deps), so the
 * runtime client and headless e2e tests can both use them.
 */
object FsTree {

    /**
     * Delete a directory tree WITHOUT following symbolic links. Symlinks are
     * deleted as links; the files they point to are left untouched.
     *
     * Never use [File.deleteRecursively] for harness-owned trees: the JDK File
     * API follows symlinks while descending, and the DSH boot heal creates
     * `$DSH_HOME/profiles/node_modules` entries pointing INTO the harness
     * checkout — a naive recursive delete therefore deletes thousands of
     * tracked checkout files (the 2026-08-29 dev-clone incident; see
     * specs/tech-stack.md "DSH checkout discipline").
     */
    fun deleteNoFollow(root: File) {
        val path = root.toPath()
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                // Includes symlinks: without FOLLOW_LINKS, walkFileTree reports a
                // link itself as a file entry and never descends into its target.
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc == null) Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
