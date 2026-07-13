# cloud-itonami-isco-6122

Open Occupation Blueprint for **ISCO-08 6122**: Poultry Producers.

**Maturity: `:implemented`** — PoultryAdvisor ⊣ PoultryProductionGovernor
as a langgraph StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt), modeled on
cloud-itonami-isco-4311's bookkeeping actor. 13 tests / 27 assertions
green. The governor never dispatches hardware — it only gates what
the coop-monitoring robot below may execute.

The feed-dispense HARD invariant — arithmetic, not welfare judgement:
a proposed feed dose divided by the registered bird count must not
exceed the coop's registered per-bird ceiling. `:administer-medication`
and `:approve-outbreak-containment` **always** escalate to human
sign-off regardless of confidence, per this repo's Trust Controls
(business-model.md) — no veterinary medication or outbreak decision is
ever auto-committed.

This repository designs a forkable OSS business for an independent poultry producer: a coop-monitoring robot performs feed dispensing and egg collection under a governor-gated actor, so the operator keeps their own flock and health records instead of renting a closed poultry-management SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a coop-monitoring robot performs feed dispensing, egg collection and health sensing under an actor that proposes
actions and an independent **Poultry Production Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
administering veterinary medication, or a disease-outbreak containment decision) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
flock management plan + feed schedule + health protocol
        |
        v
Poultry Advisor -> Poultry Production Governor -> feed/monitor-health, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `6122`). Required capabilities:

- :robotics
- :telemetry
- :optimization
- :dmn
- :bpmn
- :audit-ledger
- :forms

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
