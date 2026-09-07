#!/usr/bin/env python3
"""Une portee de job tient ce qu elle promet, ou elle rend un job vert qui n a rien juge (#5297).

    python3 .github/scripts/verifie_portees_de_ci.py
    python3 .github/scripts/verifie_portees_de_ci.py --auto-test

Le chantier #5294 conditionne des jobs par une ETAPE plutot que par un `paths:`, pour qu ils
rendent toujours un verdict. Le dispositif a donc un mode de panne propre, et c est le faux vert :
une portee qui ne correspond a rien fait ecrire « sans objet » a un job qui aurait du juger, et
personne ne le voit, puisque le job finit vert.

## Les cinq confrontations, et celle qui manque

1. **Bijection job / portee.** Toute cle de `PORTEES` est appelee par exactement un job, tout appel
   nomme une cle connue, et la cle EST celle du job. Sans ce dernier point, un copier-coller donnerait
   a `capturer` la portee de `paquet` sans que rien ne rougisse.
2. **Aucune porte decorative.** Un job qui porte le pas `portee` conditionne au moins une etape sur
   sa sortie, et reciproquement. C est un trou reel : `verifie_conditions_de_job.py` ne lit que les
   `if:` de JOB, et seulement quand le job porte un `needs:`. Rien ne regardait les `if:` d ETAPE.
3. **Aucun chemin mort.** Chaque ligne d une portee resout vers au moins un fichier du depot. Elle
   attrape le renommage, la suppression et la faute de frappe, c est-a-dire les trois facons dont une
   portee cesse de correspondre sans qu on le decide.
4. **Le dispositif s eprouve lui-meme.** Chaque portee nomme son propre atelier et les deux scripts
   du mecanisme. Sans quoi on changerait une portee, et le job ne tournerait pas pour le verifier.
5. **Aucun chemin ECRIT dans le job n echappe a sa portee.** Les chemins litteraux des `run:` du job,
   confrontes a ce que sa portee couvre.

**Ce qu il ne verifie PAS, et qui doit etre lu ici plutot que suppose** : qu une portee nomme les
VRAIES dependances d un job reste un JUGEMENT, pas une deduction. La cinquieme confrontation ne voit
que ce que le job ecrit noir sur blanc ; une dependance implicite - un plugin Maven, une ressource
chargee au vol - lui echappe par construction. C est la meme reserve que
`verifie_inventaires_ci.py` porte pour ses classes surveillees : qu une entree MERITE d y etre reste
une decision.

## L exhaustivite se derive, elle ne s enumere pas

Tout job d un atelier declenche sur `pull_request` est dans `PORTEES` ou dans `INCONDITIONNELS` avec
sa raison ECRITE. Un job neuf sans l un ni l autre rougit. C est l idiome des exemptions nommees de
`verifie_verdicts_declares.HORS_PORTEE` : une liste avec son motif, jamais un compte.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from porte_du_job import INCONDITIONNELS, PORTEES, correspond

# Un appel, c est `porte_du_job.py <cle>` sur UNE MEME LIGNE, et la cle ne commence pas par un tiret.
# Les deux bords ont ete trouves par la garde sur elle-meme : `\s+` traverse les retours a la ligne,
# donc `chmod +x ... porte_du_job.py` suivi de `python3` rendait « python3 » pour une cle ; et
# `--auto-test` entre dans `[A-Za-z0-9_-]+` puisque le tiret y figure.
APPEL = re.compile(r"porte_du_job\.py[^\S\n]+(?!-)([A-Za-z0-9_][A-Za-z0-9_-]*)")
SORTIE = "steps.portee.outputs.concerne"
MECANISME = (".github/scripts/porte_du_job.py", ".github/scripts/_portee.py")
# Un jeton qui RESSEMBLE a un chemin du depot. L existence tranche ensuite : c est elle qui ecarte
# les faux positifs, pas le motif.
# ⟨le groupe de fin est FACULTATIF, et c est tout le correctif de #5432⟩ Il etait obligatoire, si
# bien qu un fichier de la RACINE n etait jamais extrait : `pom.xml`, `mvnw`, `pyproject.toml` et les
# trois `mkdocs*.yml` echappaient a la cinquieme confrontation, soit 20 chemins sur les douze
# portees. La phrase de la regle 5 promettait « aucun chemin ECRIT dans le job », l expression en
# tenait « aucun chemin CONTENANT UNE BARRE OBLIQUE ».
JETON = re.compile(r"[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.*-]+)*")
# Un script qui LIT `GITHUB_BASE_SHA` derive sa portee du diff. Lance sans elle, il retombe sur son
# repli - « verifie tout » - et le mecanisme reste ecrit, eprouve, documente, et INERTE.
BASE = "GITHUB_BASE_SHA"


def ateliers_de_demande(racine: pathlib.Path) -> dict[str, dict]:
    """Les ateliers declenches sur `pull_request`, par nom de fichier."""
    trouves = {}
    for f in sorted((racine / ".github" / "workflows").glob("*.yml")):
        try:
            charge = yaml.safe_load(f.read_text(encoding="utf-8"))
        except yaml.YAMLError:
            continue
        if not isinstance(charge, dict):
            continue
        # `on:` est lu par YAML comme le booleen True, et c est un piege classique de ce format.
        declencheurs = charge.get(True) or charge.get("on") or {}
        if isinstance(declencheurs, dict) and "pull_request" in declencheurs:
            trouves[f.name] = charge
    return trouves


def etapes(job: dict) -> list[dict]:
    return [e for e in (job.get("steps") or []) if isinstance(e, dict)]


def lecteurs_de_base(racine: pathlib.Path) -> set[str]:
    """Les scripts qui LISENT le SHA de base, par opposition a ceux qui en parlent.

    La recherche brute sur le texte se trompe, et ce garde en a fait les frais a sa premiere
    ecriture : il porte lui-meme la constante `BASE = "GITHUB_BASE_SHA"`, et s est donc accuse de ne
    pas se passer une base qu il ne lit pas.

    C est la mise en garde que le depot porte deja, dans `verifie_inventaires_ci.porte_l_option` :
    « un script qui compte un motif present dans un COMMENTAIRE est faux par construction ; le
    commentaire cite la chose, il ne la fait pas ». On lit donc l ARBRE : un script lit la base quand
    il appelle `os.environ.get(...)` ou `os.environ[...]` dessus.
    """
    import ast as _ast

    trouves = set()
    for dossier in (".github/scripts", "scripts/adr", "scripts/methode"):
        for f in sorted((racine / dossier).glob("*.py")):
            texte = f.read_text(encoding="utf-8", errors="ignore")
            if BASE not in texte:
                continue
            try:
                arbre = _ast.parse(texte)
            except SyntaxError:
                # On ne conclut pas sur ce qu on ne sait pas lire, et on penche du cote BRUYANT :
                # exiger a tort une base se voit, l oublier est le silence que ce garde combat.
                trouves.add(f.name)
                continue
            for noeud in _ast.walk(arbre):
                lu = None
                if isinstance(noeud, _ast.Call) and isinstance(noeud.func, _ast.Attribute):
                    if noeud.func.attr == "get" and noeud.args:
                        lu = noeud.args[0]
                elif isinstance(noeud, _ast.Subscript):
                    lu = noeud.slice
                if isinstance(lu, _ast.Constant) and lu.value == BASE:
                    trouves.add(f.name)
                    break
    return trouves


# ⟨ce qu un job NOMME sans le lire⟩ La cinquieme confrontation lit les jetons d un `run:`, et un
# `run:` contient aussi des messages. `outillage-release` ecrit
# `throw new Error('parserOpts non herites de .releaserc.json : ...')` : il LIT
# `.github/release/release.config.js`, qui est dans sa portee, et ne fait que NOMMER l autre.
#
# C est « le commentaire cite la chose, il ne la fait pas », un cran plus profond : un commentaire
# se reconnait syntaxiquement, une chaine de message non. Et les guillemets ne departagent pas,
# puisque `require('./.github/release/release.config.js')` est cite de la meme facon et constitue
# une vraie lecture.
#
# On DECLARE donc, avec le motif, plutot que d inferer - c est ce que l ADR 5398 vient de trancher
# pour la porte locale, et l idiome de `verifie_verdicts_declares.HORS_PORTEE` : une liste avec sa
# raison, jamais un compte. Une entree qui ne correspond plus a rien fait rougir.
NOMMES_SANS_ETRE_LUS: dict[tuple[str, str], str] = {
    ("outillage-release", ".releaserc.json"): "cite dans le texte d un `throw new Error(...)` ; "
    "le job lit `.github/release/release.config.js`, qui est dans sa portee",
}


def _ignores(racine: pathlib.Path, chemins: set[str]) -> set[str]:
    """Ceux que git ignore, et qui ne peuvent donc JAMAIS paraitre dans un diff.

    Sans ce filtre, l elargissement de #5432 confrontait `target`, le repertoire de construction :
    il est ignore, mais il EXISTE des qu on a construit - et la porte locale l ecrit elle-meme en y
    posant `batterie-durees.json`. Le verdict de la cinquieme confrontation aurait donc dependu de
    si quelqu un avait bati avant de le lancer, ce qui est exactement une mesure dont la premisse
    varie sans qu on le sache.

    Hors depot git - le depot jouet de l auto-test - rien n est ignore, et c est le bon repli : on
    confronte plutot que de se taire.
    """
    if not chemins:
        return set()
    rendu = subprocess.run(
        ["git", "-C", str(racine), "check-ignore", "--stdin"],
        input="\n".join(sorted(chemins)),
        capture_output=True,
        text=True,
        check=False,
    )
    return {l.strip() for l in rendu.stdout.splitlines() if l.strip()}


def chemins_ecrits(job: dict, racine: pathlib.Path) -> set[str]:
    """Les chemins du depot que les `run:` de ce job citent litteralement.

    Le `./` de tete est normalise : `./.github/release/release.config.js` et
    `.github/release/release.config.js` designent le meme fichier, et seul le second est comparable
    a une portee.
    """
    trouves = set()
    for etape in etapes(job):
        for jeton in JETON.findall(str(etape.get("run") or "")):
            nu = jeton.strip("'\"").rstrip(".,;:")
            nu = nu.removeprefix("./")
            # ⟨`nu` non vide⟩ Le motif elargi de #5432 peut rendre une chaine vide apres le
            # nettoyage, et `(racine / "").exists()` est VRAI : c est la racine du depot. Sans ce
            # garde-fou, la racine entrait dans les chemins confrontes et faisait echouer l appel a
            # `git check-ignore`, donc le filtre des ignores ne filtrait plus rien.
            if nu and "*" not in nu and (racine / nu).exists():
                trouves.add(nu)
    return trouves - _ignores(racine, trouves)


def couvre(chemin: str, surveilles: list[str], racine: pathlib.Path) -> bool:
    """Ce chemin est-il couvert par l un des motifs ?

    Un job cite souvent un REPERTOIRE - `npm ci --prefix .github/release`. Un repertoire n est pas
    un fichier, et `.github/release/**` ne l apparie donc pas litteralement ; il le couvre pourtant,
    puisqu il couvre tout ce qu il contient. On tranche par le contenu plutot que par la forme, sans
    quoi la garde crierait sur une portee juste - et un dispositif qui crie sur du juste apprend a
    etre ignore.
    """
    if any(correspond(chemin, s) for s in surveilles):
        return True
    dossier = racine / chemin
    if dossier.is_dir():
        dedans = [
            f.relative_to(racine).as_posix() for f in _arbre(racine) if f.is_relative_to(dossier)
        ]
        return bool(dedans) and any(correspond(d, s) for d in dedans for s in surveilles)
    return False


def juger(
    racine: pathlib.Path | None = None,
    portees: dict[str, str] | None = None,
    inconditionnels: dict[str, str] | None = None,
    exemptions: dict[tuple[str, str], str] | None = None,
) -> int:
    """Les cinq confrontations, plus l exhaustivite. Les donnees sont injectables pour l auto-test."""
    racine = racine or pathlib.Path(__file__).resolve().parents[2]
    portees = PORTEES if portees is None else portees
    inconditionnels = INCONDITIONNELS if inconditionnels is None else inconditionnels
    exemptions = NOMMES_SANS_ETRE_LUS if exemptions is None else exemptions
    ecarts: list[str] = []

    ateliers = ateliers_de_demande(racine)
    if not ateliers:
        print("❌ Aucun atelier declenche sur `pull_request` : la garde ne peut rien confronter.")
        return 1

    appels: dict[str, list[str]] = {}
    tous_les_jobs: set[str] = set()
    # Une meme cle YAML peut vivre dans DEUX ateliers : `build` est celle de `docs.yml` et celle de
    # `maven.yml`. Une portee posee sur cette cle serait ambigue, et le pas la passe en argument sans
    # dire d ou il vient.
    ateliers_du_job: dict[str, list[str]] = {}

    for fichier, charge in ateliers.items():
        for cle, job in (charge.get("jobs") or {}).items():
            if not isinstance(job, dict):
                continue
            tous_les_jobs.add(cle)
            ateliers_du_job.setdefault(cle, []).append(fichier)
            pas = etapes(job)

            nommes = [n for e in pas for n in APPEL.findall(str(e.get("run") or ""))]
            for n in nommes:
                appels.setdefault(n, []).append(cle)
                if n != cle:
                    ecarts.append(
                        f"le job `{cle}` de {fichier} appelle la portee `{n}` : la cle doit etre celle du job"
                    )

            # 2. Aucune porte decorative, dans les deux sens.
            porte = any(e.get("id") == "portee" for e in pas)
            gardees = [e for e in pas if SORTIE in str(e.get("if") or "")]
            if porte and not gardees:
                ecarts.append(
                    f"le job `{cle}` de {fichier} porte le pas `portee` mais ne conditionne aucune etape dessus"
                )
            if gardees and not porte:
                ecarts.append(
                    f"le job `{cle}` de {fichier} conditionne une etape sur `{SORTIE}` sans porter le pas `portee`"
                )

            # 7. Un script qui derive sa portee d une base RECOIT cette base.
            #
            # Sans elle il retombe sur « verifie tout » : le mecanisme est ecrit, eprouve, documente,
            # et il ne s exerce jamais. C est arrive entre #5356 et #5366 - le banc de mutation a ete
            # fusionne sans que l etape lui passe `GITHUB_BASE_SHA`, donc il a continue de muter les
            # quarante-sept gardes a chaque demande, exactement comme avant.
            #
            # Une invocation `--auto-test` est ECARTEE : elle eprouve le garde sur des donnees
            # injectees, elle ne juge pas le depot, et elle n a donc aucune base a recevoir. C est la
            # meme distinction que `verifie-batterie-locale.py` fait pour sa population.
            for etape in pas:
                commande = str(etape.get("run") or "")
                if not commande:
                    continue
                for nu in lecteurs_de_base(racine):
                    for ligne in commande.splitlines():
                        if nu in ligne and "--auto-test" not in ligne:
                            if BASE not in str(etape.get("env") or {}):
                                ecarts.append(
                                    f"le job `{cle}` de {fichier} lance `{nu}` sans lui passer "
                                    f"`{BASE}` : il derivera sa portee d une base absente, donc il "
                                    "verifiera TOUT en silence"
                                )
                            break

            # 5. Aucun chemin ecrit dans le job n echappe a sa portee.
            if cle in portees:
                surveilles = [l.strip() for l in portees[cle].splitlines() if l.strip()]
                vus = sorted(chemins_ecrits(job, racine))
                for ecrit in vus:
                    if couvre(ecrit, surveilles, racine) or (cle, ecrit) in exemptions:
                        continue
                    ecarts.append(
                        f"le job `{cle}` lance `{ecrit}`, que sa portee ne couvre pas : "
                        "une modification de ce fichier ne le reveillerait pas"
                    )
                # L exemption est CONFRONTEE : une entree qui ne correspond plus a rien a survecu a
                # ce qu elle exemptait, et elle exempterait alors un vrai ecart sans que rien ne le
                # dise. C est la moitie qui fait d une liste un inventaire (ADR 5373, ADR 5398).
                for (job_exempte, chemin), motif in exemptions.items():
                    if job_exempte == cle and chemin not in vus:
                        ecarts.append(
                            f"le job `{cle}` n ecrit plus `{chemin}`, exempte pour : {motif}. "
                            "L exemption a survecu a son motif, retirez-la"
                        )

    # 1. Bijection, dans l autre sens.
    for cle in sorted(portees):
        combien = len(appels.get(cle, []))
        if combien == 0:
            ecarts.append(f"la portee `{cle}` n est appelee par aucun job")
        elif combien > 1:
            ecarts.append(f"la portee `{cle}` est appelee par {combien} jobs : {appels[cle]}")

    for nomme in sorted(appels):
        if nomme not in portees:
            ecarts.append(f"le job appelle la portee `{nomme}`, absente de PORTEES")

    # 3 et 4.
    for cle, bloc in sorted(portees.items()):
        surveilles = [l.strip() for l in bloc.splitlines() if l.strip()]
        for motif in surveilles:
            if not any(
                correspond(str(p.relative_to(racine).as_posix()), motif) for p in _arbre(racine)
            ):
                ecarts.append(
                    f"la portee `{cle}` nomme `{motif}`, qui ne correspond a aucun fichier du depot"
                )
        for outil in MECANISME:
            if outil not in surveilles:
                ecarts.append(
                    f"la portee `{cle}` ne nomme pas `{outil}` : elle ne s eprouve pas elle-meme"
                )
        atelier = [f for f, c in ateliers.items() if cle in (c.get("jobs") or {})]
        for f in atelier:
            attendu = f".github/workflows/{f}"
            if attendu not in surveilles:
                ecarts.append(f"la portee `{cle}` ne nomme pas son propre atelier `{attendu}`")

    # La cle d une portee est ambigue si deux ateliers portent un job de ce nom.
    for cle in sorted(portees):
        chez = ateliers_du_job.get(cle, [])
        if len(chez) > 1:
            ecarts.append(
                f"la portee `{cle}` est ambigue : {len(chez)} ateliers portent un job de ce nom "
                f"({', '.join(chez)}). Renommez l un des jobs avant de le porter."
            )

    # 6. L exhaustivite.
    for cle in sorted(tous_les_jobs):
        if cle not in portees and cle not in inconditionnels:
            ecarts.append(
                f"le job `{cle}` tourne sur chaque demande sans etre dans PORTEES ni dans "
                "INCONDITIONNELS : declarez sa portee, ou la raison pour laquelle il tourne toujours"
            )

    if ecarts:
        print(f"❌ {len(ecarts)} ecart(s) sur les portees de la CI :")
        for e in ecarts:
            print(f"   · {e}")
        print()
        print("   Une portee qui ne correspond a rien fait ecrire « sans objet » a un job qui")
        print("   aurait du juger, et le job finit VERT. C est le faux vert que cette garde existe")
        print("   pour empecher.")
        return 1

    print(
        f"✔ {len(portees)} portee(s) et {len(inconditionnels)} job(s) inconditionnel(s) : "
        f"les {len(tous_les_jobs)} jobs de demande sont declares."
    )
    return 0


def _arbre(racine: pathlib.Path) -> list[pathlib.Path]:
    """Les fichiers versionnes, en cache : on resout des globs contre eux."""
    if _CACHE.get(str(racine)) is None:
        import subprocess

        sortie = subprocess.run(
            # `--others --exclude-standard` : un fichier NEUF n est pas encore suivi, et le
            # declarer mort ferait rougir la garde sur le lot qui l introduit.
            ["git", "-C", str(racine), "ls-files", "--cached", "--others", "--exclude-standard"],
            capture_output=True,
            text=True,
            check=False,
        )
        if sortie.returncode == 0:
            _CACHE[str(racine)] = [racine / l for l in sortie.stdout.splitlines() if l]
        else:
            _CACHE[str(racine)] = [p for p in racine.rglob("*") if p.is_file()]
    return _CACHE[str(racine)]


_CACHE: dict[str, list[pathlib.Path] | None] = {}


ATELIER = """name: Faux
on:
  pull_request:
jobs:
  un:
    steps:
      - name: "La portee"
        id: portee
        run: |
          chmod +x .github/scripts/porte_du_job.py
          python3 .github/scripts/porte_du_job.py --auto-test
          python3 .github/scripts/porte_du_job.py un
      - name: "Le travail cher"
        if: steps.portee.outputs.concerne == 'oui'
        run: echo cher
  deux:
    steps:
      - name: "Rien"
        run: echo rien
"""

# Une portee coherente pour le depot jouet : elle nomme son atelier, les deux scripts du mecanisme,
# et rien qui n existe pas.
PORTEE_SAINE = """
.github/workflows/faux.yml
.github/scripts/porte_du_job.py
.github/scripts/_portee.py
"""


def _monter(bac: pathlib.Path) -> pathlib.Path:
    """Un depot jouet coherent, que chaque cas degradera d une seule facon."""
    import shutil
    import subprocess

    depot = bac / "depot"
    shutil.rmtree(depot, ignore_errors=True)
    (depot / ".github/workflows").mkdir(parents=True)
    (depot / ".github/scripts").mkdir(parents=True)
    (depot / ".github/workflows/faux.yml").write_text(ATELIER, encoding="utf-8")
    for outil in MECANISME:
        (depot / outil).write_text("# jouet\n", encoding="utf-8")
    # Un lecteur de base, pour le cas qui exige qu on la lui passe. Il la LIT, il n en parle pas :
    # c est la distinction que `lecteurs_de_base` fait par l arbre.
    (depot / ".github/scripts/lit_la_base.py").write_text(
        'import os\nbase = os.environ.get("GITHUB_BASE_SHA")\n', encoding="utf-8"
    )
    # Et un qui la MENTIONNE seulement : il ne doit RIEN exiger.
    (depot / ".github/scripts/en_parle.py").write_text(
        'BASE = "GITHUB_BASE_SHA"  # on la nomme, on ne la lit pas\n', encoding="utf-8"
    )
    # `_arbre` interroge git ; un depot jouet non versionne le fait retomber sur `rglob`, ce qui est
    # le comportement voulu, mais autant eprouver le chemin nominal.
    subprocess.run(["git", "-C", str(depot), "init", "-q"], check=False)
    subprocess.run(["git", "-C", str(depot), "add", "-A"], check=False)
    _CACHE.clear()
    return depot


def _sans_appel(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    p["orpheline"] = PORTEE_SAINE


def _appel_inconnu(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    del p["un"]
    i["un"] = "raison quelconque"


def _chemin_mort(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    p["un"] = PORTEE_SAINE + "src/inexistant/**\n"


def _sans_mecanisme(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    p["un"] = ".github/workflows/faux.yml\n.github/scripts/_portee.py\n"


def _sans_son_atelier(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    p["un"] = ".github/scripts/porte_du_job.py\n.github/scripts/_portee.py\n"


def _job_non_declare(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    del i["deux"]


def _base_non_passee(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    """Un job lance un lecteur de base SANS la lui passer : le mecanisme sera inerte."""
    f = d / ".github/workflows/faux.yml"
    f.write_text(
        f.read_text(encoding="utf-8").replace(
            "        run: echo rien",
            "        run: python3 .github/scripts/lit_la_base.py",
        ),
        encoding="utf-8",
    )


def _mention_sans_lecture(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    """Un job lance un script qui MENTIONNE la base sans la lire : rien ne doit etre exige.

    Sans ce cas, une detection par le texte passerait le precedent en accusant tout le monde, et ce
    garde crierait sur du juste - il s est accuse LUI-MEME a sa premiere ecriture, portant la
    constante `BASE` sans lire la variable.
    """
    f = d / ".github/workflows/faux.yml"
    f.write_text(
        f.read_text(encoding="utf-8").replace(
            "        run: echo rien",
            "        run: python3 .github/scripts/en_parle.py",
        ),
        encoding="utf-8",
    )


def _porte_decorative(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    """La porte decide, et plus personne n ecoute : le `if:` a saute."""
    f = d / ".github/workflows/faux.yml"
    f.write_text(
        f.read_text(encoding="utf-8").replace(
            "        if: steps.portee.outputs.concerne == 'oui'\n", ""
        ),
        encoding="utf-8",
    )


def _condition_orpheline(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    """Une etape attend une sortie que plus aucun pas ne produit : elle ne tournera JAMAIS."""
    f = d / ".github/workflows/faux.yml"
    f.write_text(
        f.read_text(encoding="utf-8").replace("        id: portee\n", ""), encoding="utf-8"
    )


def _cle_ambigue(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    """Un SECOND atelier porte un job `un`, et la portee ne sait plus lequel elle designe."""
    (d / ".github/workflows/jumeau.yml").write_text(
        ATELIER.replace("name: Faux", "name: Jumeau"), encoding="utf-8"
    )


def _fichier_de_racine_hors_portee(
    d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict
) -> None:
    """Un job lit un fichier de la RACINE que sa portee ne couvre pas.

    C est le cas que la cinquieme confrontation promettait d attraper et laissait passer : son motif
    exigeait une barre oblique, si bien qu un nom nu ne lui parvenait jamais (#5432).
    """
    (d / "reglage.toml").write_text("# jouet\n", encoding="utf-8")
    f = d / ".github/workflows/faux.yml"
    # Le job `un` et non `deux` : `deux` est INCONDITIONNEL, et la cinquieme confrontation ne
    # s applique qu aux jobs qui portent une portee.
    avant = f.read_text(encoding="utf-8")
    apres = avant.replace("        run: echo cher", "        run: cat reglage.toml")
    assert apres != avant, "le degradeur n a rien change : le cas ne prouverait rien"
    f.write_text(apres, encoding="utf-8")


def _exemption_perimee(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    """Une exemption declaree pour un chemin que le job n ecrit plus.

    Sans ce cas, la table de #5432 serait une liste que rien ne tient : une entree y survivrait a ce
    qu elle exemptait, et exempterait alors un vrai ecart en silence.
    """
    n[("un", "jamais-ecrit.toml")] = "motif qui n a plus d objet"


def _chemin_ignore_par_git(d: pathlib.Path, p: dict[str, str], i: dict[str, str], n: dict) -> None:
    """Un job ecrit un chemin que git IGNORE : il ne peut jamais paraitre dans un diff.

    Le confronter rendrait le verdict dependant de l etat du disque - `target` existe des qu on a
    bati, et la porte locale l ecrit elle-meme. Le controle NEGATIF du filtre de #5432.
    """
    (d / ".gitignore").write_text("bati/\n", encoding="utf-8")
    (d / "bati").mkdir()
    (d / "bati" / "trace.txt").write_text("jouet\n", encoding="utf-8")
    import subprocess

    subprocess.run(["git", "-C", str(d), "init", "-q"], check=False)
    f = d / ".github/workflows/faux.yml"
    avant = f.read_text(encoding="utf-8")
    # ⟨le `...` n est pas decoratif⟩ Il rend un jeton que le nettoyage vide entierement, et
    # `(racine / "")` EXISTE. Sans le garde-fou, la chaine vide entre dans les chemins confrontes et
    # fait ECHOUER `git check-ignore`, donc le filtre des ignores ne filtre plus : `bati` remonte
    # alors comme un ecart. Les deux defauts de #5432 ne se manifestent QUE reunis, et un cas qui
    # n en porte qu un reste vert sous sa propre mutation.
    apres = avant.replace("        run: echo cher", "        run: ls bati ...")
    assert apres != avant, "le degradeur n a rien change"
    f.write_text(apres, encoding="utf-8")


CAS = (
    # Le controle NEGATIF d abord : une regle qui refuse tout est aussi inutile qu une regle qui
    # accepte tout. Un depot jouet coherent DOIT rester vert.
    (0, "un dépôt cohérent reste vert", None),
    (1, "une portée que personne n'appelle est vue", _sans_appel),
    (1, "un appel vers une portée absente de PORTEES est vu", _appel_inconnu),
    (1, "un chemin qui ne correspond à aucun fichier est vu", _chemin_mort),
    (1, "une portée qui ne nomme pas le mécanisme est vue", _sans_mecanisme),
    (1, "une portée qui ne nomme pas son propre atelier est vue", _sans_son_atelier),
    (1, "un job ni porté ni déclaré inconditionnel est vu", _job_non_declare),
    # La porte decorative, dans les DEUX sens. `verifie_conditions_de_job.py` ne lit que les `if:`
    # de JOB : rien ne regardait ceux des etapes, et c est la que ce dispositif-ci se conditionne.
    (1, "une porte qui ne conditionne plus aucune étape est vue", _porte_decorative),
    (1, "une étape qui attend une sortie que nul ne produit est vue", _condition_orpheline),
    (1, "une portée dont la clé vit dans deux ateliers est vue", _cle_ambigue),
    # Le mecanisme INERTE : ecrit, eprouve, documente, et jamais exerce.
    (1, "un lecteur de base lancé sans sa base est vu", _base_non_passee),
    # Et son controle negatif : mentionner la base n'est pas la lire.
    (0, "un script qui mentionne la base sans la lire n'exige rien", _mention_sans_lecture),
    # Le chemin de RACINE : sans barre oblique, il echappait au motif (#5432).
    (1, "un fichier de la racine hors de la portée est vu", _fichier_de_racine_hors_portee),
    # Et le controle dans l AUTRE sens : la table est confrontee, donc c est un inventaire.
    (1, "une exemption qui a survécu à son motif est vue", _exemption_perimee),
    # Le controle NEGATIF du filtre : ce que git ignore ne se confronte pas.
    (0, "ce que git ignore n'est pas confronté, et le jeton vide non plus", _chemin_ignore_par_git),
)


def _auto_test() -> int:
    import copy
    import tempfile

    echecs = 0
    with tempfile.TemporaryDirectory() as bac:
        for attendu, libelle, degrade in CAS:
            depot = _monter(pathlib.Path(bac))
            portees = {"un": PORTEE_SAINE}
            inconditionnels = {"deux": "il ne fait rien de cher"}
            # Le depot jouet n exempte rien : chaque cas la remplit s il en a besoin.
            exemptions: dict[tuple[str, str], str] = {}
            if degrade is not None:
                degrade(depot, portees, inconditionnels, exemptions)
            _CACHE.clear()
            obtenu = juger(
                depot,
                copy.deepcopy(portees),
                copy.deepcopy(inconditionnels),
                copy.deepcopy(exemptions),
            )
            if obtenu == attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
                echecs += 1
    _CACHE.clear()
    combien = sum(1 for c in CAS if c[0] == 1)
    print(f"\n{len(CAS)} cas, dont {combien} qui DOIVENT rougir.")
    return 1 if echecs else 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juger())
