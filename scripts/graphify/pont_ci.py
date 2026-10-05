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
import sys
from pathlib import Path

# Le noeud de fichier d'une page se choisit a UN endroit, la passe B. Cette passe en portait
# une copie, avec le meme filtre a trois dossiers : une page de la racine n'y avait jamais de
# noeud de fichier, et ses citations d'un workflow ou du pom n'etaient pas reliees (#5868).
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from _commun import cas_d_auto_test
from pont_doc_code import (
    joue_et_rend_le_journal,
    joue_sur_un_depot_fabrique,
    noeud_de_document,
    noeuds_de_page,
    pages_reliees,
)


def auto_test():
    """Les citations de D3, jouees sur un depot fabrique."""
    verifie, echecs = cas_d_auto_test()
    structure = [
        noeud_de_document("testing", "TESTING.md", "TESTING.md", node_kind="page"),
        noeud_de_document("testing_tester", "Tester", "TESTING.md", node_kind="heading"),
        noeud_de_document("docs_guide", "guide.md", "docs/guide.md", node_kind="page"),
        noeud_de_document(
            "openspec_specs_ci_spec", "spec.md", "openspec/specs/ci/spec.md", node_kind="page"
        ),
        noeud_de_document(
            "claude_skills_x_skill", "SKILL.md", ".claude/skills/x/SKILL.md", node_kind="page"
        ),
    ]
    fichiers = {
        ".github/workflows/lint.yml": "name: Quality gate\njobs:\n  lint:\n    runs-on: x\n",
        "TESTING.md": "# Tester\n\nLe portail vit dans `lint.yml`.\n",
        "docs/guide.md": "# Guide\n\nVoir `lint.yml`, et lint.yml hors de tout span.\n",
        "SECURITY.md": "# Securite\n\nRien de `lint.yml` n entre ici sans noeud de page.\n",
        "openspec/specs/ci/spec.md": "# Spec\n\nLe portail est `lint.yml`.\n",
        ".claude/skills/x/SKILL.md": "# Copie\n\nVoir `lint.yml`.\n",
    }

    def citations():
        code, _, liens = joue_sur_un_depot_fabrique(
            Path(__file__).resolve(), structure, [], fichiers
        )
        return code, sorted(
            (e["source"], e["target"]) for e in liens if e.get("context") == "code_span"
        )

    # `docs/guide.md` est le temoin : il etait deja relie. `TESTING.md` ne l'etait pas, et
    # `SECURITY.md`, que la structure ne porte pas ici, ne recoit rien : cette passe ne fabrique
    # pas de noeud de fichier.
    verifie(
        "une page de la racine qui nomme un workflow dans un span y est reliee, depuis sa page",
        citations,
        (
            0,
            [
                ("docs_guide", "github_workflows_lint_yml"),
                ("openspec_specs_ci_spec", "github_workflows_lint_yml"),
                ("testing", "github_workflows_lint_yml"),
            ],
        ),
    )

    # ⟨D2, les scripts lances⟩ un atelier d une seule etape, et le script qu elle nomme. Chaque
    # ecriture d un chemin a son cas : la passe en reliait deux sur quatre, et c est ce qui a
    # cache le defaut (#5919).
    script_connu = noeud_de_document(
        "github_scripts_x", "x.py", ".github/scripts/x.py", file_type="code"
    )

    def scripts_relies(commande, presents, structure_du_cas=()):
        atelier = f"name: CI\njobs:\n  gardes:\n    steps:\n      - run: {commande}\n"
        code, _, liens = joue_sur_un_depot_fabrique(
            Path(__file__).resolve(),
            list(structure_du_cas),
            [],
            {".github/workflows/ci.yml": atelier, **dict.fromkeys(presents, "")},
        )
        return code, sorted(
            (e["source"], e["target"]) for e in liens if e.get("context") == "ci_script"
        )

    atelier_fabrique = "github_workflows_ci_yml"
    verifie(
        "un atelier est relie au script de `.github/` qu il lance",
        lambda: scripts_relies("python3 .github/assets/w.py", [".github/assets/w.py"]),
        (0, [(atelier_fabrique, "github_assets_w_py")]),
    )
    verifie(
        "le lien atterrit sur le noeud du script deja dans le graphe, pas sur une coquille",
        lambda: scripts_relies(
            "python3 .github/scripts/x.py --verifie", [".github/scripts/x.py"], [script_connu]
        ),
        (0, [(atelier_fabrique, "github_scripts_x")]),
    )
    verifie(
        "un chemin ecrit `./.github/` est relie comme `.github/`",
        lambda: scripts_relies("./.github/scripts/v.sh", [".github/scripts/v.sh"]),
        (0, [(atelier_fabrique, "github_scripts_v_sh")]),
    )
    verifie(
        "un chemin ecrit avec `./` retrouve lui aussi le noeud deja dans le graphe",
        lambda: scripts_relies("./.github/scripts/x.py", [".github/scripts/x.py"], [script_connu]),
        (0, [(atelier_fabrique, "github_scripts_x")]),
    )
    verifie(
        "un chemin ecrit `./scripts/` est relie, sans son prefixe",
        lambda: scripts_relies("./scripts/y.sh --verifie", ["scripts/y.sh"]),
        (0, [(atelier_fabrique, "scripts_y_sh")]),
    )
    verifie(
        "un chemin ecrit `scripts/` est relie",
        lambda: scripts_relies("python3 scripts/z.py", ["scripts/z.py"]),
        (0, [(atelier_fabrique, "scripts_z_py")]),
    )
    verifie(
        "un script nomme qui n existe pas ne recoit ni noeud ni lien",
        lambda: scripts_relies("python3 .github/scripts/absent.py", ["scripts/z.py"]),
        (0, []),
    )
    verifie(
        "deux scripts d une meme etape sont relies tous les deux",
        lambda: scripts_relies(
            "python3 scripts/z.py && python3 .github/assets/w.py",
            ["scripts/z.py", ".github/assets/w.py"],
        ),
        (0, [(atelier_fabrique, "github_assets_w_py"), (atelier_fabrique, "scripts_z_py")]),
    )

    # ⟨D2, ce que la passe dit avoir lu⟩ elle compte ce qu elle AJOUTE, et « 0 vers un script »
    # se lisait de deux facons. Les deux cas rendent ce meme zero ou presque, et se distinguent
    # par ce qui a ete lu.
    def ligne_des_ateliers(commande, presents, structure_du_cas=(), aretes=()):
        atelier = f"name: CI\njobs:\n  gardes:\n    steps:\n      - run: {commande}\n"
        code, _, _, journal = joue_et_rend_le_journal(
            Path(__file__).resolve(),
            list(structure_du_cas),
            list(aretes),
            {".github/workflows/ci.yml": atelier, **dict.fromkeys(presents, "")},
        )
        return code, [ligne for ligne in journal if ligne.startswith("D2 :")]

    deja_relie = {
        "source": atelier_fabrique,
        "target": "github_scripts_x",
        "relation": "references",
    }
    trois_lues_dont_une_sans_fichier = (
        "D2 : 1 workflows lus, 3 invocations d un script dont 1 sans fichier ; ajoutes : "
        "1 jobs, 0 liens vers un profil Maven, 2 vers un script, 0 appels entre workflows"
    )
    une_lue_deja_reliee = (
        "D2 : 1 workflows lus, 1 invocations d un script dont 0 sans fichier ; ajoutes : "
        "1 jobs, 0 liens vers un profil Maven, 0 vers un script, 0 appels entre workflows"
    )
    verifie(
        "la passe dit les invocations lues et celles sans fichier, a cote de ce qu elle ajoute",
        lambda: ligne_des_ateliers(
            "python3 .github/scripts/x.py && scripts/z.py && python3 scripts/absent.py",
            [".github/scripts/x.py", "scripts/z.py"],
        ),
        (0, [trois_lues_dont_une_sans_fichier]),
    )
    verifie(
        "un script deja relie n est pas ajoute, mais il est compte parmi les invocations lues",
        lambda: ligne_des_ateliers(
            "python3 .github/scripts/x.py", [".github/scripts/x.py"], [script_connu], [deja_relie]
        ),
        (0, [une_lue_deja_reliee]),
    )
    return echecs()


if __name__ == "__main__" and "--auto-test" in sys.argv:
    raise SystemExit(auto_test())

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
# Ce que la passe a LU, a cote de ce qu elle ajoute : « 0 vers un script » disait aussi bien
# « tout etait deja relie » que « rien n a ete reconnu », et c est le second qui etait vrai.
n_invocation = n_sans_fichier = 0
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
        # scripts lances. `Path` replie deja un `./` de tete : aucun retrait a la main. Celui qui
        # vivait ici, `lstrip("./")`, retire des CARACTERES et non un prefixe : il mangeait le
        # point de `.github`, le chemin n existait plus, et le lien etait saute sans un mot. 205
        # invocations sur 321 se perdaient ainsi (#5919).
        for sc in re.findall(r"((?:\./)?(?:\.github|scripts)/[\w./-]+\.(?:sh|py|bash))", ln):
            q = Path(sc)
            n_invocation += 1
            if q.exists():
                n_script += lier(wid, noeud_fichier(q, i), "references", "ci_script", p, i)
            else:
                n_sans_fichier += 1
        # appels d'un autre workflow
        for uses in re.findall(r"uses:\s*\./\.github/workflows/([\w.-]+)", ln):
            if uses in wf_par_fichier:
                n_appel += lier(wid, wf_par_fichier[uses], "references", "ci_call", p, i)
print(
    f"D2 : {n_wf} workflows lus, {n_invocation} invocations d un script dont {n_sans_fichier} "
    f"sans fichier ; ajoutes : {n_job} jobs, {n_lien_prof} liens vers un profil "
    f"Maven, {n_script} vers un script, {n_appel} appels entre workflows"
)

# ------------------------------------------------------------------ D3 : citations
cible = {}
for f, i in wf_par_fichier.items():
    cible[f] = i
for nom, i in profils.items():
    if "-" in nom or len(nom) >= 8:  # ecarte les ids trop generiques (check, pmd)
        cible[nom] = i
cible["pom.xml"] = nid(pom)

fichier_node = noeuds_de_page(nodes.values())
n_cit = 0
for p in pages_reliees(nodes.values()):
    src = fichier_node[str(p)]
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
print(f"D3 : {n_cit} citations ajoutees, de la doc vers un workflow, un profil ou le pom")

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
