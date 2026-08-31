"""Passe D : fait entrer la chaine CI et le build dans le graphe.

`dev-docs/ci-cd-release.md` decrit maven.yml, lint.yml, mutation-*.yml... qui n'etaient
pas dans le graphe : la doc CI parlait dans le vide. Meme chose pour les profils Maven
(`quality-gate`, `mutation`, `api-live`) que les ADR citent sans cesse.

  D1 build     : pom.xml -> un noeud par <profile><id>
  D2 workflows : .github/workflows/*.yml -> noeud du workflow, ses jobs,
                 les profils Maven qu'il active (-Pxxx, valide contre pom.xml pour
                 ecarter les flags PowerShell -Path/-Process/-Pass), les scripts
                 qu'il lance, et les workflows qu'il appelle
  D3 citations : un document qui nomme `maven.yml` ou `quality-gate` dans un span de
                 code est relie au noeud correspondant (EXTRACTED, comme la passe B)
"""

import json
import re
from pathlib import Path

SRC = Path("graphify-out/.graphify_extract.json")
if not SRC.exists():
    SRC = Path("graphify-out/graph.json")
g = json.loads(SRC.read_text(encoding="utf-8"))
nodes = {n["id"]: n for n in g["nodes"]}
edges = g["edges"] if "edges" in g else g["links"]
print(f"depart : {SRC.name} - {len(nodes)} noeuds, {len(edges)} aretes")

vues = {(e["source"], e["target"], e.get("relation")) for e in edges}


def nid(path):
    return re.sub(r"[^a-z0-9]+", "_", str(path).lower()).strip("_")


def noeud(i, label, sf, ligne=1, ft="code"):
    if i not in nodes:
        nodes[i] = {
            "id": i,
            "label": label,
            "file_type": ft,
            "source_file": str(sf),
            "source_location": f"L{ligne}",
            "norm_label": label.lower(),
            "_origin": "pont",
        }
    return i


# Un fichier lance par un workflow peut deja etre dans le graphe, extrait par l'AST.
# Son identifiant y perd l'extension (`scripts_adr_rapport`) la ou nid() la garde
# (`scripts_adr_rapport_py`) : sans ce rapprochement, le renvoi fabrique un second
# noeud pour le meme fichier, et le lien CI pointe sur une coquille au lieu du script
# reellement analyse. Le noeud AST du FICHIER se reconnait a son libelle, qui est le
# nom du fichier ; ses fonctions portent le leur.
ast_par_fichier = {
    n["source_file"]: n["id"]
    for n in nodes.values()
    if n.get("_origin") == "ast"
    and n.get("source_file")
    and n.get("label") == Path(n["source_file"]).name
}


def noeud_fichier(chemin, ligne=1):
    """Le noeud AST du fichier s'il est deja dans le graphe, sinon un noeud de renvoi."""
    return ast_par_fichier.get(str(chemin)) or noeud(nid(chemin), Path(chemin).name, chemin, ligne)


def lier(s, t, rel, ctx, sf, ligne):
    if s == t or (s, t, rel) in vues:
        return 0
    vues.add((s, t, rel))
    edges.append(
        {
            "relation": rel,
            "confidence": "EXTRACTED",
            "confidence_score": 1.0,
            "source_file": str(sf),
            "source_location": f"L{ligne}",
            "weight": 1.0,
            "_origin": "pont",
            "context": ctx,
            "source": s,
            "target": t,
        }
    )
    return 1


# --------------------------------------------------------------------- D1 : pom.xml
pom = Path("pom.xml")
profils = {}
n_prof = 0
if pom.exists():
    texte = pom.read_text(encoding="utf-8", errors="ignore")
    lignes = texte.splitlines()
    pid = noeud(nid(pom), "pom.xml", pom)
    # <profile><id> uniquement : un <id> nu attrape aussi les <execution><id>
    bloc = re.search(r"<profiles>(.*)</profiles>", texte, re.S)
    vrais = set(re.findall(r"<profile>\s*<id>([\w.-]+)</id>", bloc.group(1))) if bloc else set()
    for i, ln in enumerate(lignes, 1):
        m = re.search(r"<id>([\w.-]+)</id>", ln)
        if m and m.group(1) in vrais and m.group(1) not in profils:
            nom = m.group(1)
            j = noeud(f"pom_profil_{nid(nom)}", f"profil Maven {nom}", pom, i)
            profils[nom] = j
            n_prof += lier(pid, j, "contains", "maven_profile", pom, i)
print(f"D1 : pom.xml, {len(profils)} profils Maven -> {sorted(profils)[:6]}...")

# ---------------------------------------------------------------- D2 : workflows CI
wf_dir = Path(".github/workflows")
wf_par_fichier = {}
n_wf = n_job = n_lien_prof = n_script = n_appel = 0
for p in sorted(wf_dir.glob("*.yml")) + sorted(wf_dir.glob("*.yaml")):
    lignes = p.read_text(encoding="utf-8", errors="ignore").splitlines()
    nom = next(
        (m.group(1).strip().strip("\"'") for ln in lignes if (m := re.match(r"name:\s*(.+)$", ln))),
        p.name,
    )
    wid = noeud(nid(p), f"{p.name} ({nom})", p)
    wf_par_fichier[p.name] = wid
    n_wf += 1
    dans_jobs = False
    for i, ln in enumerate(lignes, 1):
        if re.match(r"^jobs:\s*$", ln):
            dans_jobs = True
            continue
        # ne se referme que sur une VRAIE cle de premier niveau : un commentaire en
        # colonne 0 entre deux jobs fermait le bloc et faisait perdre les suivants
        if dans_jobs and re.match(r"^[A-Za-z_][\w-]*:", ln):
            dans_jobs = False
        if dans_jobs and (m := re.match(r"^  ([\w-]+):\s*$", ln)):
            j = noeud(f"{wid}_job_{nid(m.group(1))}", f"job {m.group(1)}", p, i)
            n_job += lier(wid, j, "contains", "ci_job", p, i)
        # profils Maven : valides contre pom.xml, sinon -Path/-Process passent
        for prof in re.findall(r"-P([\w,-]+)", ln):
            for nom_p in prof.split(","):
                if nom_p in profils:
                    n_lien_prof += lier(wid, profils[nom_p], "references", "maven_profile", p, i)
        # scripts lances
        for sc in re.findall(r"((?:\./)?(?:\.github|scripts)/[\w./-]+\.(?:sh|py|bash))", ln):
            q = Path(sc.lstrip("./"))
            if q.exists():
                n_script += lier(wid, noeud_fichier(q, i), "references", "ci_script", p, i)
        # appels d'un autre workflow
        for uses in re.findall(r"uses:\s*\./\.github/workflows/([\w.-]+)", ln):
            if uses in wf_par_fichier:
                n_appel += lier(wid, wf_par_fichier[uses], "references", "ci_call", p, i)
print(
    f"D2 : {n_wf} workflows, {n_job} jobs, {n_lien_prof} liens vers un profil Maven, "
    f"{n_script} vers un script, {n_appel} appels entre workflows"
)

# ------------------------------------------------------------------ D3 : citations
cible = {}
for f, i in wf_par_fichier.items():
    cible[f] = i
for nom, i in profils.items():
    if "-" in nom or len(nom) >= 8:  # ecarte les ids trop generiques (check, pmd)
        cible[nom] = i
cible["pom.xml"] = nid(pom)

fichier_node = {}
for n in nodes.values():
    sf = n.get("source_file") or ""
    if sf.startswith(("brief/", "dev-docs/", "docs/")) and n.get("file_type") == "document":
        cur = fichier_node.get(sf)
        if cur is None or len(n["id"]) < len(cur["id"]):
            fichier_node[sf] = n

docs = [p for d in ("brief", "dev-docs", "docs") for p in Path(d).rglob("*.md")]
docs += [p for p in Path(".").glob("*.md") if p.name != "CHANGELOG.md"]
n_cit = 0
for p in sorted(docs):
    src = fichier_node.get(str(p))
    if src is None:
        continue
    lignes = p.read_text(encoding="utf-8", errors="ignore").splitlines()
    dans_bloc = False
    for i, ln in enumerate(lignes, 1):
        if ln.lstrip().startswith("```"):
            dans_bloc = not dans_bloc
            continue
        spans = [ln] if dans_bloc else re.findall(r"`([^`\n]{1,120})`", ln)
        for s in spans:
            for nom, tid in cible.items():
                if re.search(r"(?<![\w.-])" + re.escape(nom) + r"(?![\w-])", s):
                    n_cit += lier(src["id"], tid, "references", "code_span", p, i)
print(f"D3 : {n_cit} citations de la doc vers un workflow, un profil ou le pom")

Path("graphify-out/.graphify_extract.json").write_text(
    json.dumps(
        {
            "nodes": list(nodes.values()),
            "edges": edges,
            "hyperedges": g.get("hyperedges", []),
            "input_tokens": 0,
            "output_tokens": 0,
        },
        ensure_ascii=False,
    ),
    encoding="utf-8",
)
print(f"\nresultat : {len(nodes)} noeuds, {len(edges)} aretes")
