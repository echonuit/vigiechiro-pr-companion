#!/usr/bin/env python3
"""Engendre et garde la matrice de traçabilité de la constitution (chantier A).

L'idée vient de CSDD : une constitution seule reste de la prose bien intentionnée ; ce qui la rend
vérifiable, c'est une matrice qui relie chaque article aux endroits qui le réalisent. La colonne qui
vaut le déplacement est la dernière, **rien ne l'applique** : elle nomme les articles qui ne sont
que des souhaits.

Le dépôt avait déjà tiré cette leçon sur deux règles, dans `ConventionsDEcritureTest` : « une
convention que seule la relecture applique n'est pas une convention, c'est un souhait ». La matrice
l'étend à tous les articles.

Elle est **engendrée**, jamais saisie : les applicateurs et les comptes vivent dans les en-têtes des
ADR, et un chiffre que le code sait recalculer ne s'écrit pas à la main (ADR 2750). `--verifie`
refuse une matrice périmée ; c'est ce refus qui empêche la constitution de mentir sur elle-même.

Aucune dépendance hors stdlib : `lint.yml` n'installe rien.
"""

import argparse
import pathlib
import re
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts" / "adr"))
from verifie_okf import RESERVES, lit_entete  # noqa: E402

CONSTITUTION = RACINE / "CONSTITUTION.md"
DECISIONS = RACINE / "dev-docs" / "decisions"

DEBUT = "<!-- matrice engendree : ne pas editer a la main -->"
FIN = "<!-- fin de la matrice engendree -->"
ARTICLE = re.compile(r"^###\s+(A\d+)\s*:\s*(.+?)\s*$", re.M)
# Un applicateur qui ressemble a un chemin de fichier ou a un identifiant de test. Le reste (de la
# prose glissee entre chevrons) ne compte pas comme un applicateur nomme.
MECANIQUE = re.compile(r"^[\w./#-]+$")


def recense(decisions: pathlib.Path, constitution: pathlib.Path) -> list[dict]:
    """Pour chaque article : son énoncé, ses applicateurs distincts, sa jurisprudence."""
    articles = ARTICLE.findall(constitution.read_text(encoding="utf-8"))
    par_article: dict[str, list[dict]] = {code: [] for code, _ in articles}
    for f in sorted(decisions.glob("*.md")):
        if f.name in RESERVES:
            continue
        entete = lit_entete(f.read_text(encoding="utf-8"))
        code = entete.get("article")
        if code in par_article:
            par_article[code].append(entete)
    lignes = []
    for code, enonce in articles:
        adrs = par_article[code]
        tenants = []
        for e in adrs:
            for t in e.get("enforced_by") or []:
                if MECANIQUE.match(str(t)) and t not in tenants:
                    tenants.append(str(t))
        lignes.append(
            {
                "code": code,
                "enonce": enonce,
                "adrs": len(adrs),
                "certaines": sum(1 for e in adrs if e.get("verification") == "certaine"),
                "tenants": tenants,
            }
        )
    return lignes


def rend(lignes: list[dict]) -> str:
    """La section de matrice, telle qu'elle doit figurer dans la constitution."""
    sortie = [
        DEBUT,
        "",
        "## Matrice de traçabilité",
        "",
        "Engendrée depuis les en-têtes des ADR par "
        "`scripts/methode/matrice-constitution.py`, et gardée par lui.",
        "",
        "| Article | Jurisprudence | Dont mécanisée | Tenu par |",
        "|---|---:|---:|---|",
    ]
    dettes = []
    for l in lignes:
        if l["tenants"]:
            tenu = ", ".join(f"`{t}`" for t in l["tenants"][:3])
            if len(l["tenants"]) > 3:
                reste = len(l["tenants"]) - 3
                tenu += f", et {reste} autre" + ("s" if reste > 1 else "")
        else:
            tenu = "**relecture seule**"
            dettes.append(l)
        sortie.append(f"| {l['code']} · {l['enonce']} | {l['adrs']} | {l['certaines']} | {tenu} |")
    sortie += [
        "",
        f"**{len(dettes)} article(s) sur {len(lignes)} ne sont tenus que par la relecture.** "
        "C'est la liste des chantiers de garde restants, et elle se lit comme un inventaire, "
        "pas comme une fatalité.",
        "",
    ]
    if dettes:
        sortie += [f"- {l['code']} · {l['enonce']}" for l in dettes] + [""]
    sortie.append(FIN)
    return "\n".join(sortie)


def remplace(texte: str, matrice: str) -> str:
    """Le document, sa matrice remplacée ou ajoutée en fin."""
    if DEBUT in texte and FIN in texte:
        avant = texte[: texte.index(DEBUT)]
        apres = texte[texte.index(FIN) + len(FIN) :]
        return avant + matrice + apres
    return texte.rstrip("\n") + "\n\n---\n\n" + matrice + "\n"


def main() -> int:
    p = argparse.ArgumentParser(description="Matrice de traçabilité de la constitution")
    p.add_argument("--verifie", action="store_true", help="refuse une matrice périmée")
    p.add_argument("--auto-test", action="store_true", help="éprouve le garde sur des fixtures")
    args = p.parse_args()

    if args.auto_test:
        return auto_test()

    texte = CONSTITUTION.read_text(encoding="utf-8")
    attendu = remplace(texte, rend(recense(DECISIONS, CONSTITUTION)))
    if args.verifie:
        if texte != attendu:
            print(
                "La matrice de la constitution est périmée.\n"
                "Relancez : python3 scripts/methode/matrice-constitution.py",
                file=sys.stderr,
            )
            return 1
        print("Matrice de la constitution à jour.")
        return 0
    CONSTITUTION.write_text(attendu, encoding="utf-8")
    print("Matrice de la constitution engendrée.")
    return 0


def auto_test() -> int:
    """Le garde doit rougir sur une matrice périmée, et se taire sur une matrice fraîche."""
    modele = (
        '---\ntype: adr\ntitle: "T"\nstatus: stable\narticle: A1\n'
        'verification: certaine\nenforced_by:\n  - "TemoinTest#cas"\n'
        "verified:\n  - by: machine:ci\n---\n\n# T\n"
    )
    echecs = []
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        decisions = racine / "decisions"
        decisions.mkdir()
        (decisions / "0001-t.md").write_text(modele, encoding="utf-8")
        const = racine / "CONSTITUTION.md"
        const.write_text(
            "### A1 : Un témoin\n\n### A2 : Un article que rien ne tient\n", encoding="utf-8"
        )

        lignes = recense(decisions, const)
        frais = remplace(const.read_text(encoding="utf-8"), rend(lignes))

        for titre, obtenu, attendu in (
            ("l'article tenu nomme son applicateur", "TemoinTest#cas" in frais, True),
            ("l'article que rien ne tient est nommé", "**relecture seule**" in frais, True),
            ("la dette est comptée", "1 article(s) sur 2" in frais, True),
            ("une matrice périmée diffère", frais != const.read_text(encoding="utf-8"), True),
        ):
            print(f"  {'✔' if obtenu == attendu else '✘'} {titre}")
            if obtenu != attendu:
                echecs.append(titre)

        # Non-vacuité : une fois écrite, la matrice doit être stable. Un générateur instable
        # rendrait le garde rouge à jamais, et son rouge cesserait de vouloir dire quelque chose.
        const.write_text(frais, encoding="utf-8")
        stable = (
            remplace(const.read_text(encoding="utf-8"), rend(recense(decisions, const))) == frais
        )
        print(f"  {'✔' if stable else '✘'} la matrice engendrée est stable")
        if not stable:
            echecs.append("stabilité")

        # Et elle doit rougir quand la jurisprudence bouge sous elle.
        (decisions / "0002-u.md").write_text(modele.replace("# T", "# U"), encoding="utf-8")
        perime = (
            remplace(const.read_text(encoding="utf-8"), rend(recense(decisions, const))) != frais
        )
        print(f"  {'✔' if perime else '✘'} une ADR ajoutée périme la matrice")
        if not perime:
            echecs.append("péremption")

    if echecs:
        print(f"\n{len(echecs)} cas en échec : {', '.join(echecs)}", file=sys.stderr)
        return 1
    print("\nAuto-test concluant : la matrice se périme quand le corpus bouge, et pas autrement.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
