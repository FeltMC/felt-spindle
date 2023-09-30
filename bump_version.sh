#!/usr/bin/env bash

NEW_BASE_VERSION="$(grep "^base_version=" ./felt-spindle/gradle.properties | cut -d'=' -f2)";
if [ "$BASE_VERSION" == "$NEW_BASE_VERSION" ]; then
	(( PATCH_VERSION++ ));
else
	gh api \
		--method PATCH \
		-H "Accept: application/vnd.github+json" \
		-H "X-GitHub-Api-Version: 2022-11-28" \
		"${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/actions/variables/BASE_VERSION" \
		-f name='BASE_VERSION' \
		-f value="${NEW_BASE_VERSION}";
	PATCH_VERSION=0;
fi

gh api \
	--method PATCH \
	-H "Accept: application/vnd.github+json" \
	-H "X-GitHub-Api-Version: 2022-11-28" \
	"${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/actions/variables/PATCH_VERSION" \
	-f name='PATCH_VERSION' \
	-f value="${PATCH_VERSION}";

echo "BASE_VERSION=${NEW_BASE_VERSION}" | tee -a "$GITHUB_ENV";
echo "PATCH_VERSION=${PATCH_VERSION}" | tee -a "$GITHUB_ENV";
