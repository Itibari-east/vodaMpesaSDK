# Contributor guide

Contributions to the Vodacom M-Pesa Tanzania Java SDK are welcome. Start with an issue so the
problem, proposed approach, and scope can be discussed before implementation.

## 1. Raise an issue first

1. Search [existing issues](https://github.com/Itibari-east/vodaMpesaSDK/issues) and open pull requests
   to check whether the problem is already reported or someone is working on it.
2. If an issue exists, add relevant details and comment that you would like to work on it.
3. Otherwise, [open an issue](https://github.com/Itibari-east/vodaMpesaSDK/issues/new) before writing code.
4. Wait for a maintainer to confirm the scope and approach before starting implementation. This also
   applies to documentation changes; keep the issue brief for a small correction.

For a bug report, include:

- A clear title and description of the problem.
- SDK version, Java version, and Spring Boot version if applicable.
- Whether the issue occurs in sandbox or production.
- Steps to reproduce, expected behavior, and actual behavior.
- A minimal code example and sanitized error details or request/response fixtures.

For a feature or documentation request, explain the use case, the proposed behavior, and any public
API changes. For endpoint changes, identify the relevant documentation section and any portal-specific
behavior. Distinguish observed behavior from assumptions.

Never include API keys, session tokens, security credentials, authorization headers, customer phone
numbers, or real payment data in public issues, commits, or screenshots. Replace these with synthetic
values. Do not disclose an exploitable security vulnerability in a public issue; contact a maintainer
privately through an available repository/profile contact channel to arrange a private report.

## 2. Set up your development environment

Use Java 17 or later, Git, and the included Maven wrapper. Fork the repository, clone your fork,
and create a branch from the repository's current default branch:

```bash
git clone https://github.com/YOUR_USERNAME/vodaMpesaSDK.git
cd vodaMpesaSDK
git remote add upstream https://github.com/Itibari-east/vodaMpesaSDK.git
git switch -c fix/123-short-description
./mvnw clean verify
```

Replace `YOUR_USERNAME` and `123-short-description` with your fork owner and issue details.
Use a descriptive branch name such as `fix/123-session-expiry`, `feat/124-status-query`, or
`docs/125-configuration-example`. On Windows, use `mvnw.cmd`; an installed Maven can also run
`mvn clean verify`.

The tests use a localhost mock API and generated test keys. They require permission to open local
sockets, but do not require M-Pesa credentials or live transactions. Maven may need internet access
to download build dependencies on the first run.

## 3. Make a focused change

- Keep the change within the agreed issue scope. Discuss scope changes in the issue first.
- Follow the existing `VodaMpesaSdk → VodaMpesaService → VodaMpesaClient` structure and code style.
- Keep the core usable from plain Java and Spring integration optional.
- Preserve Java 17 compatibility and existing public API behavior unless a breaking change was agreed.
- Keep configuration single-account; tenant configuration is not part of this SDK.
- Use the documented wire field names. Explain and document any portal-specific assumptions.
- Update the README and public API Javadocs when behavior or usage changes.
- Avoid unrelated formatting, dependency upgrades, generated files, and version changes.

Payment behavior needs particular care: request acceptance is different from completed payment,
callbacks can be duplicated, and a timeout can leave the outcome unknown. Do not introduce automatic
payment resubmission or treat a caller-supplied response code as proof of settlement. Preserve the
application's responsibility for durable storage, transaction ownership, and idempotent fulfilment.

## 4. Validate your work

Add regression tests for bug fixes and meaningful tests for new behavior. Depending on the change,
cover wire payloads, validation, error handling, session behavior, callbacks, or Spring configuration.
Use synthetic data and mock endpoints; automated tests must not initiate real payments.

Before submitting code changes, run:

```bash
./mvnw clean verify
git diff --check
```

For a documentation-only change, check the examples, links, Markdown formatting, and property names;
a full Java test run is not required. State which checks you ran in the pull request. If a check cannot
run, explain the limitation rather than reporting it as passed.

## 5. Open a pull request

Push your branch to your fork and open a pull request against the upstream repository's current
default branch. Use a draft pull request if the work is not ready for review.

Include:

- A clear title describing the change.
- A link to the agreed issue, using `Closes #123` when the pull request fully resolves it.
- The problem and resulting behavior, with a short before/after example where useful.
- Tests or documentation checks performed and their results.
- Any compatibility impact, configuration changes, limitations, or remaining work.

Keep one pull request focused on one issue or a closely related, agreed set of changes. Do not commit
credentials, local IDE settings, `target/`, or other build artifacts. Publishing Maven artifacts and
creating releases are maintainer responsibilities, not part of submitting a contribution.

## 6. Respond to review

Address review comments on the same branch and explain any requested change you disagree with.
Resolve merge conflicts and rerun the relevant checks after code changes. Maintainers decide when
a pull request is ready to merge; opening an issue or pull request does not guarantee acceptance.

Keep discussions respectful, specific, and focused on the code and documented behavior.

## License

By submitting a contribution, you agree that it may be distributed under this project's
[MIT License](LICENSE). Only contribute code and documentation you have the right to share.
