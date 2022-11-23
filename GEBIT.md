# Release Management

## Notes
Requires Maven 3.8.1 to build!

## Branches
gebit-3.x is our main branch, but not really following upstream's master branch.
Instead, it follows the release-xxx tags. We try to merge the release tags into
gebit-3.x, but once in a while, upstream diverges, so we rebase and force-push our
commits.

## Tags
Our releases are created on a temporary branch that ends with a version-change commit
containing the changes from

```mvn versions:set -DnewVersion=3.x.y-gebitZ```

That commit is then tagged with

```release-3.x.y-gebitZ```

The tag is then pushed with

```git push --tags```

and the temporary branch discarded.

## Release Artifact
An artifact is released by copying the new HPI package from target/ to
[gebit-build-docker/jenkins-master/share/refs/plugins/](https://gitlab.local.gebit.de/gebit-build/gebit-build-docker/-/tree/master/jenkins-master/share/ref/plugins)
and releasing a new jenkins-master version.
