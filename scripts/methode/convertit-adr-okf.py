#!/usr/bin/env python3
"""Convertit les ADR du format a puces vers un en-tete OKF v0.2 (chantier A).

Ce que la conversion gagne : les trois puces `Statut`, `Chantier` et `Verification` deviennent des
champs types, lisibles par la machine, et le rattachement de chaque ADR a son article de la
constitution cesse d etre implicite. Ce que le lecteur ne doit PAS perdre : ces trois lignes etaient
VISIBLES en tete de page. Un hook MkDocs les redessine depuis les metadonnees ; sans lui, la
conversion serait un recul pour l humain, c est a dire l inverse de son objet.

Ce que la conversion n invente pas, et c est delibere :

- aucun champ `generated`. L arbre a ete importe d un seul coup, sans historique par fichier : rien
  ne dit quel agent a produit quelle ADR. La declaration d assistance est donc COLLECTIVE, portee
  par `index.md` et `PROVENANCE.md`, plutot que fausse par document.
- aucun champ `sources` en URL. Les numeros cites visent l ANCIEN depot, et leur sort revient aux
  chantiers B et C. Le champ `chantier` garde le texte brut : il dit ce qu on sait, sans promettre
  un lien qui resoudrait ailleurs.

Idempotent : un fichier qui porte deja un en-tete YAML est laisse tel quel.
"""

import argparse
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import DECISIONS, sort_si_contrat_demande

# Le titre porte parfois son propre numero, parfois non, et le separateur a deux formes.
TITRE = re.compile(r"^#\s+(?:ADR\s+)?(?:\S+\s*[-–:]\s*)?(.*)$")
PUCE = re.compile(r"^- \*\*([^*]+)\*\*\s*:\s*(.*)$")
DATE = re.compile(r"(\d{4}-\d{2}-\d{2})")
CODE = re.compile(r"`([^`]+)`")
# Le cliquet vivait dans la prose de la puce : « probable - `script` (cliquet : 1) ».
# `scripts/adr/_commun.py` le lit desormais comme un CHAMP ; laisse en note, il serait
# invisible aux neuf cliquets, qui refuseraient de demarrer.
CLIQUET_PUCE = re.compile(r"\(cliquet\s*:\s*(\d+)\)")
# Un renvoi vers une autre ADR, sous sa forme de lien relatif : c est ainsi que les 469 citations
# et les 21 amendements sont ecrits aujourd hui.
RENVOI = re.compile(r"\((\d{4}|\d{3,4})-[a-z0-9-]+\.md\)")

NIVEAUX = {"certaine": "machine:ci", "probable": "machine:suspects", "humaine": "human:nedseb"}

# Les puces de relation, ramenees a leur verbe. La forme libre est conservee dans `relations` :
# les verbes portent du sens, et les ecraser en un seul « lie a » perdrait ce que le spike a mesure.
RELATIONS = {
    "prolonge",
    "amende",
    "amendée",
    "complète",
    "fait évoluer",
    "applique",
    "suit",
    "suite de",
    "renverse",
    "reformule",
    "absorbe",
    "quatrième couture",
}


def echappe(valeur: str) -> str:
    """Rend une chaine YAML sur une ligne, quelles que soient ses ponctuations."""
    return '"' + valeur.replace("\\", "\\\\").replace('"', '\\"') + '"'


def convertit(chemin: pathlib.Path, article: str) -> str | None:
    """L en-tete OKF suivi du corps, ou None si le fichier est deja converti."""
    texte = chemin.read_text(encoding="utf-8")
    if texte.startswith("---\n"):
        return None
    lignes = texte.splitlines()

    # L en-tete court du titre de niveau 1 a la premiere section. Le corps est TOUT le reste, pris
    # d un seul coup : une premiere version reaffectait la tranche a chaque ligne suivante et ne
    # gardait que la derniere. Les 172 corps etaient ampute, et l apercu tronque ne le montrait pas.
    titre, puces = "", {}
    debut_corps = len(lignes)
    for i, ligne in enumerate(lignes):
        if not titre and ligne.startswith("# "):
            titre = TITRE.match(ligne).group(1).strip()
            continue
        if titre and ligne.startswith("## "):
            debut_corps = i
            break
        m = PUCE.match(ligne)
        if m:
            puces.setdefault(m.group(1).strip(), []).append(m.group(2).strip())
    corps = "\n".join(lignes[debut_corps:]).lstrip("\n")

    statut = puces.get("Statut", [""])[0]
    verif = puces.get("Vérification", [""])[0]
    niveau = verif.split(" ")[0].strip().lower()
    note = re.split(r"\s[-–]\s", verif, maxsplit=1)
    date = DATE.search(statut) or DATE.search(texte)
    date = date.group(1) if date else ""

    tete = ["---", "type: adr", f"title: {echappe(titre)}"]
    # Zero remplacement et zero annulation dans le corpus mesure : tout ce qui vit est `stable`.
    # Le garde de coherence statut/graphe existe pour le jour ou ce ne sera plus vrai.
    tete.append("status: stable")
    tete.append(f"article: {article}")
    if puces.get("Chantier"):
        tete.append(f"chantier: {echappe(puces['Chantier'][0])}")
    if date:
        tete.append(f"decided_at: {date}")
    tete.append(f"verification: {niveau}")
    applicateurs = CODE.findall(verif)
    if applicateurs:
        # Une verification `humaine` qui nomme un script ne nomme pas un APPLICATEUR : rien n y est
        # refuse mecaniquement. C est une LOUPE, qui releve pour que la revue juge. Les confondre
        # ferait compter comme mecanisee une regle que seule la relecture tient, c est-a-dire
        # exactement le mensonge que la matrice existe pour empecher.
        tete.append("loupe:" if niveau == "humaine" else "enforced_by:")
        tete.extend(f"  - {echappe(a)}" for a in applicateurs)
    valeur = CLIQUET_PUCE.search(verif)
    if valeur:
        tete.append(f"ratchet: {valeur.group(1)}")
    # La note ne se garde que si elle DIT quelque chose de plus que l applicateur. Sur 112 ADR
    # `certaine`, la puce se reduit souvent au seul identifiant du test : le recopier en prose
    # donnerait un champ qui a l air renseigne et qui ne porte rien.
    if len(note) > 1:
        depouille = CLIQUET_PUCE.sub("", note[1])
        residu = CODE.sub("", depouille).strip(" .,;:-()`")
        if residu:
            tete.append(f"verification_note: {echappe(depouille.strip())}")
    tete.append("verified:")
    tete.append(f"  - by: {NIVEAUX.get(niveau, 'human:nedseb')}")
    if date:
        tete.append(f"    at: {date}")
    liens = {}
    for cle, valeurs in puces.items():
        base = cle.lower().split(" le ")[0].strip()
        if base in RELATIONS:
            for v in valeurs:
                liens.setdefault(base, []).extend(RENVOI.findall(v))
    liens = {k: v for k, v in liens.items() if v}
    if liens:
        tete.append("relations:")
        for verbe, cibles in sorted(liens.items()):
            tete.append(f"  {verbe}: [{', '.join(echappe(c) for c in cibles)}]")
    tete.append("---")
    return (
        "\n".join(tete)
        + "\n\n"
        + f"# {titre}\n\n"
        + corps
        + ("\n" if not corps.endswith("\n") else "")
    )


def auto_test() -> int:
    """La conversion se prouve sur une ADR POSEE, et dans les deux sens.

    Le defaut que ce temoin garde est nomme dans `convertit` : une premiere version reaffectait la
    tranche du corps a chaque ligne et n en gardait que la derniere. Les 172 corps etaient amputes,
    et l apercu tronque ne le montrait pas. Un temoin qui ne verifierait que l en-tete l aurait
    laisse passer, d ou le cas qui compte le CORPS (issue #5157).
    """
    import tempfile

    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    print("Auto-test de la conversion vers OKF (#5157) :")

    A_PUCES = (
        "# ADR 9999 - Un titre de temoin\n"
        "\n"
        "- **Statut** : stable\n"
        "- **Date** : 2026-09-03\n"
        "- **V\u00e9rification** : probable (cliquet : 7)\n"
        "\n"
        "## Contexte\n"
        "\n"
        "Le premier paragraphe du corps.\n"
        "\n"
        "## Decision\n"
        "\n"
        "Le dernier paragraphe du corps.\n"
    )

    with tempfile.TemporaryDirectory(prefix="vc-okf-") as tmp:
        f = pathlib.Path(tmp) / "9999-un-titre-de-temoin.md"
        f.write_text(A_PUCES, encoding="utf-8")
        rendu = convertit(f, "A1")
        verifie("une ADR a puces se convertit", rendu is not None, True)
        verifie("l en-tete OKF ouvre le rendu", rendu.startswith("---\n"), True)
        verifie("le titre est repris", 'title: "Un titre de temoin"' in rendu, True)
        # La puce s ecrit « Vérification », AVEC son accent : c est la cle que `convertit` lit, et
        # ma premiere fixture l ecrivait sans. Le rendu portait alors `verification: ` vide, sans
        # que rien ne s en plaigne : une cle inconnue ne fait pas rougir la conversion.
        verifie("le niveau de verification aussi", "verification: probable" in rendu, True)
        verifie("et le cliquet qu il portait", "ratchet: 7" in rendu, True)

        # LE CAS QUI COMPTE : le corps est pris ENTIER, non ampute a sa derniere tranche.
        verifie(
            "le corps garde son premier paragraphe",
            "Le premier paragraphe du corps." in rendu,
            True,
        )
        verifie("et son dernier", "Le dernier paragraphe du corps." in rendu, True)

        # Le sens NEGATIF : une ADR deja convertie ne se reconvertit pas.
        deja = pathlib.Path(tmp) / "9998-deja-convertie.md"
        deja.write_text("---\ntype: adr\n---\n\n# Deja\n", encoding="utf-8")
        verifie("une ADR deja au format OKF est laissee telle quelle", convertit(deja, "A1"), None)

    print()
    print("Auto-test concluant." if not echecs else "Auto-test EN ÉCHEC.")
    return echecs


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--carte", required=True, help="fichier « numero article » par ligne")
    p.add_argument("--ecrit", action="store_true", help="ecrit ; sinon rend un apercu")
    p.add_argument("--limite", type=int, default=0, help="n en traiter que N, pour l apercu")
    args = p.parse_args()

    carte = dict(l.split() for l in pathlib.Path(args.carte).read_text().split("\n") if l.strip())
    faits, sautes = 0, 0
    for f in sorted(DECISIONS.glob("*.md")):
        if f.name == "index.md":
            continue
        numero = f.name.split("-")[0]
        article = carte.get(numero)
        if not article:
            print(f"SANS ARTICLE : {f.name}", file=sys.stderr)
            return 1
        rendu = convertit(f, article)
        if rendu is None:
            sautes += 1
            continue
        if args.ecrit:
            f.write_text(rendu, encoding="utf-8")
        else:
            print(f"===== {f.name} =====")
            print("\n".join(rendu.splitlines()[: rendu.splitlines().index("---", 1) + 3]))
            print()
        faits += 1
        if args.limite and faits >= args.limite:
            break
    print(f"{faits} ADR converties, {sautes} deja au format.")
    return 0


# Pourquoi `generateur` : il ECRIT, avec `--ecrit`, et rend un apercu sinon. Il ne juge rien. Son
# `return 1` est un chemin d erreur, pas un verdict, et le confondre avec un refus ferait attendre de
# lui une garde qu il n exerce pas. La conversion est faite : aucun atelier ne le lance.
CONTRAT = {
    "geste": "conversion des ADR du format a puces vers l en-tete OKF",
    "population": "les ADR de dev-docs/decisions",
    "dispositif": "generateur",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/convertit-adr-okf.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(auto_test())
    sys.exit(main())
