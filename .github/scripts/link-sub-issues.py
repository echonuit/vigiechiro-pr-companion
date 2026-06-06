#!/usr/bin/env python3
"""Lie les tâches à leur issue chapeau (epic) comme sous-issues natives GitHub.

Pour un repo (ou tous les forks d'équipe de l'org Classroom) :
  - groupe les issues par tag `[feature]` présent dans le titre ;
  - dans un groupe, l'issue SANS `N/M —` est l'epic (issue chapeau),
    celles AVEC `N/M —` sont les tâches (triées par N) ;
  - ajoute chaque tâche comme SOUS-ISSUE NATIVE de l'epic (idempotent) ;
  - réécrit la todo-liste `## Sous-tâches` de l'epic pour citer les vrais `#numéros`
    (mapping par position). Idempotent (re-run = no-op). --no-rewrite pour désactiver.

Dry-run par défaut ; --apply pour écrire.

Usages :
  # En local (enseignant·e) : tous les forks d'équipe
  python3 link-sub-issues.py --apply
  # Un repo précis (nom court -> org S201-2026, ou owner/name complet)
  python3 link-sub-issues.py --apply --repo vigiechiro-pr-companion-le-corbeau-ewan
  # En CI (bootstrap-issues.yml) : le repo courant
  python3 link-sub-issues.py --apply --repo "$GITHUB_REPOSITORY"
"""
import argparse
import json
import re
import subprocess
import sys

ORG = "IUTInfoAix-S201-2026"  # utilisé seulement pour l'énumération des forks
FORK_RE = re.compile(r"^vigiechiro-pr-companion-.+$")  # exclut le repo nu + le template
CHILD_RE = re.compile(r"\b(\d+)\s*/\s*\d+\s*[—-]")  # '1/3 —'
TAG_RE = re.compile(r"\[([^\]]+)\]")
BULLET_PH = re.compile(r"^(\s*-\s*\[[ xX]\]\s*)\*\*[^*]+\*\*")  # puce avec placeholder en gras
BULLET_NUM = re.compile(r"^(\s*-\s*\[[ xX]\]\s*)#(\d+)\b")  # puce déjà '#N'


def gh(args, check=True, stdin=None):
    r = subprocess.run(["gh", *args], input=stdin, capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"gh {' '.join(args)} -> {r.returncode}\n{r.stderr.strip()}")
    return r


def gh_json(args):
    return json.loads(gh(args).stdout or "[]")


def slug(repo):
    return repo if "/" in repo else f"{ORG}/{repo}"


def list_team_repos():
    repos = gh_json(["repo", "list", ORG, "--limit", "200", "--json", "name"])
    return [f"{ORG}/{r['name']}" for r in sorted(repos, key=lambda x: x["name"])
            if FORK_RE.match(r["name"])]


def list_issues(s):
    data = gh_json(["api", f"repos/{s}/issues?state=all&per_page=100"])
    if len(data) >= 100:
        print(f"  ⚠ {s}: >=100 entrées /issues, pagination non gérée", file=sys.stderr)
    return [i for i in data if "pull_request" not in i]  # exclut les PRs


def group_epics(issues):
    by_tag = {}
    for i in issues:
        m = TAG_RE.search(i["title"])
        if m:
            by_tag.setdefault(m.group(1), []).append(i)
    epics = []
    for grp in by_tag.values():
        children = [i for i in grp if CHILD_RE.search(i["title"])]
        parents = [i for i in grp if not CHILD_RE.search(i["title"])]
        if len(parents) == 1 and children:
            children.sort(key=lambda i: int(CHILD_RE.search(i["title"]).group(1)))
            epics.append((parents[0], children))
    return epics


def existing_sub_numbers(s, epic_number):
    return {c["number"] for c in gh_json(["api", f"repos/{s}/issues/{epic_number}/sub_issues"])}


def add_sub_issue(s, epic_number, child_id):
    gh(["api", "--method", "POST", f"repos/{s}/issues/{epic_number}/sub_issues",
        "-F", f"sub_issue_id={child_id}"])


def set_body(s, number, body):
    payload = json.dumps({"body": body})
    gh(["api", "--method", "PATCH", f"repos/{s}/issues/{number}", "--input", "-"], stdin=payload)


def rewrite_todo(body, children):
    """Dans '## Sous-tâches', remplace le k-ième placeholder en gras par '#<num>'.

    Renvoie (new_body, n_remplaces, n_deja_numerotes, n_puces_mappees).
    Idempotent : une puce déjà '#N' est comptée (deja) sans être modifiée.
    """
    lines = (body or "").splitlines()
    out, in_section = [], False
    replaced = already = mapped = 0
    for line in lines:
        if re.match(r"^##\s+Sous-t[âa]ches", line):
            in_section = True
            out.append(line)
            continue
        if in_section and re.match(r"^##\s+", line):
            in_section = False
        if in_section and mapped < len(children):
            if BULLET_PH.match(line):
                line = BULLET_PH.sub(rf"\g<1>#{children[mapped]['number']}", line, count=1)
                replaced += 1
                mapped += 1
            elif BULLET_NUM.match(line):
                already += 1
                mapped += 1
        out.append(line)
    new_body = "\n".join(out)
    if body and body.endswith("\n"):
        new_body += "\n"
    return new_body, replaced, already, mapped


def process_repo(s, apply, do_rewrite):
    epics = group_epics(list_issues(s))
    print(f"\n### {s}  ({len(epics)} epic(s))")
    n_links = n_rewrites = 0
    for epic, children in epics:
        tag = TAG_RE.search(epic["title"]).group(1)
        existing = existing_sub_numbers(s, epic["number"])
        kids = " ".join(f"#{c['number']}" for c in children)
        print(f"  epic #{epic['number']} [{tag}] -> {kids}"
              + (f"  (déjà liées: {sorted(existing)})" if existing else ""))
        for c in [c for c in children if c["number"] not in existing]:
            if apply:
                add_sub_issue(s, epic["number"], c["id"])
            print(f"     {'+ lié' if apply else '~ (dry) lierait'} #{c['number']}")
            n_links += 1
        if do_rewrite:
            new_body, repl, alr, mapped = rewrite_todo(epic.get("body", ""), children)
            if repl > 0 and mapped == len(children):
                if apply:
                    set_body(s, epic["number"], new_body)
                print(f"     {'✎ todo réécrite' if apply else '~ (dry) réécrirait'} ({repl} puce(s))")
                n_rewrites += 1
            elif alr == len(children) and repl == 0:
                pass  # déjà à jour, silencieux
            else:
                print(f"     ⚠ todo non réécrite (epic #{epic['number']}: "
                      f"{repl} remplacées + {alr} déjà / {len(children)} tâches)")
    return n_links, n_rewrites


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--apply", action="store_true", help="écrit réellement (sinon dry-run)")
    ap.add_argument("--no-rewrite", action="store_true", help="ne pas réécrire la todo-liste")
    ap.add_argument("--repo", action="append",
                    help="repo (nom court -> org, ou owner/name) ; répétable. "
                         "Défaut : tous les forks d'équipe de l'org.")
    args = ap.parse_args()

    slugs = [slug(r) for r in args.repo] if args.repo else list_team_repos()
    print(f"[{'APPLY' if args.apply else 'DRY-RUN'}] {len(slugs)} repo(s) ; "
          f"rewrite todo = {not args.no_rewrite}")
    tl = tr = 0
    for s in slugs:
        try:
            l, r = process_repo(s, args.apply, not args.no_rewrite)
            tl += l
            tr += r
        except Exception as e:
            print(f"  ✖ {s}: {e}", file=sys.stderr)
    print(f"\n=== Total : {tl} lien(s), {tr} todo réécrite(s) "
          f"({'appliqués' if args.apply else 'simulés'}) ===")


if __name__ == "__main__":
    main()
