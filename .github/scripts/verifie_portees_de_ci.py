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
JETON = re.compile(r"[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.*-]+)+")


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
            if "*" not in nu and (racine / nu).exists():
                trouves.add(nu)
    return trouves


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
) -> int:
    """Les cinq confrontations, plus l exhaustivite. Les donnees sont injectables pour l auto-test."""
    racine = racine or pathlib.Path(__file__).resolve().parents[2]
    portees = PORTEES if portees is None else portees
    inconditionnels = INCONDITIONNELS if inconditionnels is None else inconditionnels
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

            # 5. Aucun chemin ecrit dans le job n echappe a sa portee.
            if cle in portees:
                surveilles = [l.strip() for l in portees[cle].splitlines() if l.strip()]
                for ecrit in sorted(chemins_ecrits(job, racine)):
                    if not couvre(ecrit, surveilles, racine):
                        ecarts.append(
                            f"le job `{cle}` lance `{ecrit}`, que sa portee ne couvre pas : "
                            "une modification de ce fichier ne le reveillerait pas"
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
    # `_arbre` interroge git ; un depot jouet non versionne le fait retomber sur `rglob`, ce qui est
    # le comportement voulu, mais autant eprouver le chemin nominal.
    subprocess.run(["git", "-C", str(depot), "init", "-q"], check=False)
    subprocess.run(["git", "-C", str(depot), "add", "-A"], check=False)
    _CACHE.clear()
    return depot


def _sans_appel(d: pathlib.Path, p: dict[str, str], i: dict[str, str]) -> None:
    p["orpheline"] = PORTEE_SAINE


def _appel_inconnu(d: pathlib.Path, p: dict[str, str], i: dict[str, str]) -> None:
    del p["un"]
    i["un"] = "raison quelconque"


def _chemin_mort(d: pathlib.Path, p: dict[str, str], i: dict[str, str]) -> None:
    p["un"] = PORTEE_SAINE + "src/inexistant/**\n"


def _sans_mecanisme(d: pathlib.Path, p: dict[str, str], i: dict[str, str]) -> None:
    p["un"] = ".github/workflows/faux.yml\n.github/scripts/_portee.py\n"


def _sans_son_atelier(d: pathlib.Path, p: dict[str, str], i: dict[str, str]) -> None:
    p["un"] = ".github/scripts/porte_du_job.py\n.github/scripts/_portee.py\n"


def _job_non_declare(d: pathlib.Path, p: dict[str, str], i: dict[str, str]) -> None:
    del i["deux"]


def _porte_decorative(d: pathlib.Path, p: dict[str, str], i: dict[str, str]) -> None:
    """La porte decide, et plus personne n ecoute : le `if:` a saute."""
    f = d / ".github/workflows/faux.yml"
    f.write_text(
        f.read_text(encoding="utf-8").replace(
            "        if: steps.portee.outputs.concerne == 'oui'\n", ""
        ),
        encoding="utf-8",
    )


def _condition_orpheline(d: pathlib.Path, p: dict[str, str], i: dict[str, str]) -> None:
    """Une etape attend une sortie que plus aucun pas ne produit : elle ne tournera JAMAIS."""
    f = d / ".github/workflows/faux.yml"
    f.write_text(
        f.read_text(encoding="utf-8").replace("        id: portee\n", ""), encoding="utf-8"
    )


def _cle_ambigue(d: pathlib.Path, p: dict[str, str], i: dict[str, str]) -> None:
    """Un SECOND atelier porte un job `un`, et la portee ne sait plus lequel elle designe."""
    (d / ".github/workflows/jumeau.yml").write_text(
        ATELIER.replace("name: Faux", "name: Jumeau"), encoding="utf-8"
    )


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
            if degrade is not None:
                degrade(depot, portees, inconditionnels)
            _CACHE.clear()
            obtenu = juger(depot, copy.deepcopy(portees), copy.deepcopy(inconditionnels))
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
