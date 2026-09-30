#!/usr/bin/env python3
"""ADR 5087 - Une version qu aucun check de demande n exerce se declare, elle ne se devine pas.

La demande #5078 bumpait `pitest-maven` de 1.25.8 a 1.30.0. Ses **dix-neuf checks sont passes au
vert**, et aucun n avait execute pitest. Trois bumps ont eu ce sort - #61, #2269, #5078 - et la
question « quel check aurait rougi si mon changement etait faux » n a ete posee sur le troisieme qu a
cause d un rouge de runner sans rapport, qui avait fait ouvrir le journal.

## Ce que ce garde confronte, et pourquoi PAS un diff

Il compare la valeur que le `pom.xml` porte a celle que le manifeste declare **verifiee a la main**.
Une divergence refuse, en nommant le profil et ce qui l exercerait.

Le premier dessin lisait le DIFF de la demande, et deux mesures l ont refuse :

- `corps-pr.yml` cheche la ref de FUSION en profondeur 1. Il n y a pas de base a comparer, et
  l approfondir pour un garde serait payer un `fetch` a chaque demande ;
- un garde qui refuse « une demande qui touche une version » refuserait **tous** les bumps a jamais,
  puisque rien dans un diff ne dit qu on a verifie. Il lui faut donc de toute facon une attestation.

Le manifeste EST cette attestation, et le depot en a deja le patron : `scripts/methode/relus.txt`
porte une empreinte de javadoc, et si elle change le fichier redevient a relire. Personne n a a s en
souvenir - changer la chose invalide la marque.

## La carte se DERIVE, elle ne se tient pas

C est ce qui retire le cout que l issue redoutait. Les profils viennent du `pom.xml`, ce qui les
active vient des flux, et la population exposee se deduit des deux. Mesure du 2026-09-30 :

    profil       active par                          versions portees
    ecj          -P, dans maven.yml (pull_request)   2   -> exercees
    mutation     -P, dans mutation-*.yml (schedule)  2   -> EXPOSEES
    les 7 autres -P ou <os>                          0

## Deux pieges de derivation, mesures, et comment ils sont tenus

**Un grep de `-P` rend deux flux sur huit a tort.** `winget.yml` porte `-Process`, `-PassThru` et
`-Path`, qui sont des drapeaux PowerShell ; `lint.yml` porte `multi-PR` dans un nom d etape. D ou la
RESOLUTION contre les noms que le pom declare, plutot qu une extraction libre - le nom contre la
resolution, comme #5544 deux heures plus tot.

**Trois profils sur neuf s activent par `<os>` et non par `-P`** : `jpackage-linux`, `-windows`,
`-mac`. Un garde qui ne lirait que `-P` les classerait « jamais exerces ». C est sans consequence
aujourd hui puisqu aucun des trois ne porte de version, et ce serait une fausse assurance le jour ou
l un en porterait une. Ils sont donc NOMMES dans la sortie plutot que tus.

## Ce qu il ne fait PAS

Il ne teste pas pitest, et il ne dit pas si un bump fonctionne. Il refuse le SILENCE : que la question
soit posee la ou rien ne la rappelle. Le filet qui repond, lui, est nocturne et vit dans
`mutation-model.yml` et `mutation-ihm.yml`, dont l en-tete refuse explicitement de juger une demande.
"""

from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import RACINE_DEPOT, refuse, sort_si_contrat_demande

MAVEN = "{http://maven.apache.org/POM/4.0.0}"
MANIFESTE = pathlib.Path("scripts/methode/versions-verifiees.txt")

# Une propriete referencee par un artefact : `${pitest.version}`. Les versions de profil sont des
# PROPRIETES et non des litteraux, et c est ce qui decide de ce que ce garde lit - la valeur vit dans
# `<properties>`, a une indirection de la declaration du plugin.
PROPRIETE = re.compile(r"^\$\{([A-Za-z0-9_.-]+)\}$")

# Les declencheurs qui font juger une DEMANDE. `push` n en est pas un : il juge apres la fusion.
DECLENCHEURS_DE_DEMANDE = ("pull_request", "pull_request_target")


def _pom(racine: pathlib.Path) -> ET.Element:
    return ET.parse(racine / "pom.xml").getroot()


def _texte(noeud: ET.Element, balise: str, defaut: str = "") -> str:
    trouve = noeud.find(f"{MAVEN}{balise}")
    return defaut if trouve is None or trouve.text is None else trouve.text.strip()


def profils(racine: pathlib.Path | None = None) -> dict[str, dict]:
    """Chaque profil du pom : les proprietes de version qu il porte, et s il s active par `<os>`.

    Une version ne compte que si le plugin n existe PAS dans le build principal : sinon le build
    principal la porte, et tout flux qui compile l exerce.
    """
    ou = racine or RACINE_DEPOT
    pom = _pom(ou)
    build = pom.find(f"{MAVEN}build")
    principaux = set()
    if build is not None:
        for p in build.iter(f"{MAVEN}plugin"):
            principaux.add(f"{_texte(p, 'groupId', '(defaut)')}:{_texte(p, 'artifactId', '?')}")

    trouves: dict[str, dict] = {}
    for prof in pom.iter(f"{MAVEN}profile"):
        nom = _texte(prof, "id", "?")
        versions: dict[str, str] = {}
        for p in prof.iter(f"{MAVEN}plugin"):
            art = f"{_texte(p, 'groupId', '(defaut)')}:{_texte(p, 'artifactId', '?')}"
            brute = _texte(p, "version")
            trouve = PROPRIETE.match(brute)
            if trouve and art not in principaux:
                versions[trouve.group(1)] = art
            for d in p.iter(f"{MAVEN}dependency"):
                brute = _texte(d, "version")
                trouve = PROPRIETE.match(brute)
                if trouve:
                    dep = f"{_texte(d, 'groupId', '?')}:{_texte(d, 'artifactId', '?')}"
                    versions[trouve.group(1)] = dep
        trouves[nom] = {
            "versions": versions,
            "active_par_os": prof.find(f"{MAVEN}activation") is not None,
        }
    return trouves


def valeurs(racine: pathlib.Path | None = None) -> dict[str, str]:
    """Les `<properties>` du pom : nom de propriete -> valeur."""
    props = _pom(racine or RACINE_DEPOT).find(f"{MAVEN}properties")
    if props is None:
        return {}
    return {
        e.tag.replace(MAVEN, ""): (e.text or "").strip() for e in props if isinstance(e.tag, str)
    }


def activations(
    racine: pathlib.Path | None = None, connus: set[str] | None = None
) -> dict[str, set[str]]:
    """Pour chaque profil CONNU du pom, les declencheurs des flux qui l activent par `-P`.

    **La resolution contre `connus` est le point.** Un grep de `-P` rend deux flux sur huit a tort :
    `-Process` et `-PassThru` sont des drapeaux PowerShell, `multi-PR` est un mot compose dans un nom
    d etape. Extraire librement fabriquerait des profils qui n existent pas, et un garde qui invente
    sa population rend un verdict sur ce qu il n a pas lu.
    """
    ou = racine or RACINE_DEPOT
    noms = connus if connus is not None else set(profils(ou))
    trouves: dict[str, set[str]] = {n: set() for n in noms}
    dossier = ou / ".github" / "workflows"
    if not dossier.is_dir():
        return trouves
    for flux in sorted(dossier.glob("*.yml")):
        texte = flux.read_text(encoding="utf-8")
        cites = set()
        for groupe in re.findall(r"-P\s*([A-Za-z0-9_,-]+)", texte):
            cites.update(m for m in groupe.split(",") if m in noms)
        if not cites:
            continue
        for d in _declencheurs(texte):
            for nom in cites:
                trouves[nom].add(d)
    return trouves


def _declencheurs(texte: str) -> set[str]:
    """Les declencheurs d un flux, lus dans son bloc `on:`.

    **Lu par motif et non par un analyseur YAML**, et ce n est pas de la paresse : en YAML 1.1 la cle
    `on` se lit comme le BOOLEEN `True`, donc `donnees["on"]` rend `KeyError` sur tout flux du depot.
    Un analyseur exigerait de connaitre ce piege pour rendre le bon resultat ; le motif n en depend
    pas.
    """
    debut = re.search(r"^on:\s*$", texte, re.M)
    if debut is None:
        return set()
    reste = texte[debut.end() :]
    fin = re.search(r"^\S", reste, re.M)
    bloc = reste[: fin.start()] if fin else reste
    return set(re.findall(r"^\s{2}([a-z_]+):", bloc, re.M))


def exposees(racine: pathlib.Path | None = None) -> dict[str, tuple[str, str]]:
    """Les proprietes qu aucun flux de DEMANDE n exerce : propriete -> (profil, artefact)."""
    ou = racine or RACINE_DEPOT
    carte = profils(ou)
    actives = activations(ou, connus=set(carte))
    trouves: dict[str, tuple[str, str]] = {}
    for nom, detail in carte.items():
        if not detail["versions"]:
            continue
        if actives.get(nom, set()) & set(DECLENCHEURS_DE_DEMANDE):
            continue
        for prop, artefact in detail["versions"].items():
            trouves[prop] = (nom, artefact)
    return trouves


def declarees(racine: pathlib.Path | None = None) -> dict[str, str]:
    """Le manifeste : propriete -> valeur verifiee a la main. Un manifeste ABSENT rend un vide."""
    chemin = (racine or RACINE_DEPOT) / MANIFESTE
    if not chemin.is_file():
        return {}
    trouves = {}
    for ligne in chemin.read_text(encoding="utf-8").splitlines():
        nu = ligne.strip()
        if not nu or nu.startswith("#"):
            continue
        morceaux = nu.split()
        if len(morceaux) >= 2:
            trouves[morceaux[0]] = morceaux[1]
    return trouves


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, pour que `lus` les compte (issue #5015).

    Le `pom.xml`, les flux, et le manifeste. Compter les PROPRIETES exposees serait plus parlant et
    plus faux : une derivation qui casse rend zero propriete exposee, donc zero suspect, et ce zero
    passerait pour un succes.
    """
    ou = racine or RACINE_DEPOT
    lus = [ou / "pom.xml"]
    dossier = ou / ".github" / "workflows"
    if dossier.is_dir():
        lus += sorted(dossier.glob("*.yml"))
    manifeste = ou / MANIFESTE
    if manifeste.is_file():
        lus.append(manifeste)
    return [f for f in lus if f.is_file()]


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    ou = racine or RACINE_DEPOT
    dans_le_pom = valeurs(ou)
    dit = declarees(ou)
    trouves = []
    for prop, (profil, artefact) in sorted(exposees(ou).items()):
        porte = dans_le_pom.get(prop, "(absente du pom)")
        atteste = dit.get(prop)
        if atteste is None:
            trouves.append(
                f"{prop} = {porte}  ({artefact}, profil « {profil} ») : rien ne l atteste"
            )
        elif atteste != porte:
            trouves.append(
                f"{prop} : le pom porte {porte}, le manifeste atteste {atteste}"
                f"  ({artefact}, profil « {profil} »)"
            )
    return trouves


def hors_de_la_carte(racine: pathlib.Path | None = None) -> list[str]:
    """Les profils qui s activent par `<os>`, NOMMES plutot que tus.

    Ils sont invisibles a une lecture des `-P`, et trois sur neuf le sont au 2026-09-30. Sans
    consequence tant qu aucun ne porte de version - ce que `exposees` verifierait de toute facon -
    mais un garde qui les tairait laisserait croire que sa carte est complete.
    """
    return sorted(n for n, d in profils(racine).items() if d["active_par_os"])


CONTRAT = {
    "geste": "version d artefact exposee dont le manifeste ne dit rien, ou dit autre chose",
    "population": "les proprietes referencees par un artefact de profil qu aucun flux de demande n active",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/verifie_scripts.py#test_5087_versions_hors_des_checks",
    "decision": "ADR 5087",
    "chemins": """
pom.xml
.github/workflows/**
scripts/methode/versions-verifiees.txt
scripts/adr/5087-versions-hors-des-checks.py
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if hors := hors_de_la_carte():
        print(
            "Profils actives par « os » et non par « -P », donc hors de la lecture des flux : "
            + ", ".join(hors)
            + "\n  Aucun ne porte de version aujourd hui ; le jour ou l un en portera une, sa"
            " couverture se decidera.\n"
        )
    # Un INVARIANT rend son verdict lui-meme, sans `rapporte` : celui-ci confronte un compte a un
    # cliquet, et lit donc un `ratchet:` dans l en-tete de l ADR. Une divergence entre le pom et le
    # manifeste n est jamais une dette qui descend - elle est fausse ou elle n est pas - donc il n y a
    # pas de cliquet a declarer, et en declarer un a zero dirait qu il pourrait monter.
    exposition = exposees()
    print(f"VERSIONS EXPOSEES | lus={len(fichiers())} | exposees={len(exposition)}")
    for prop, (profil, artefact) in sorted(exposition.items()):
        print(f"  {prop}  ({artefact}, profil « {profil} »)")

    trouves = suspects()
    if not trouves:
        sys.exit(0)
    refuse(
        f"{len(trouves)} version(s) exposee(s) que le manifeste n atteste pas :\n  "
        + "\n  ".join(trouves),
        f"Jouez le profil a la main sur la version du pom, puis inscrivez-la dans"
        f" {MANIFESTE} avec ce qui l atteste. Aucun flux declenche par une demande n exerce"
        f" ces artefacts : c est pourquoi rien d autre ne le dira.",
    )
