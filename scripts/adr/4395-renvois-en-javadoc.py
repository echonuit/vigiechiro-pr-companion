#!/usr/bin/env python3
"""Plancher sur les renvois d issue portes par la javadoc de production.

Un bloc de javadoc qui cite `#3498` donne au lecteur l acces a la discussion qui a produit la regle
qu il documente. La javadoc de production cite 3 111 issues distinctes, dans 983 fichiers. Une
resorption qui raccourcit ce bloc peut en emporter sans que rien ne le voie : un renvoi perdu ne
casse pas la compilation, ne fait pas rougir un test, et ne se remarque pas. Il cesse simplement
d ouvrir.

**Pourquoi ce garde existe maintenant.** Le lot 2 du portage (#4394) va relire 713 blocs. La ligne
d origine a deja fait ce travail sur un arbre dont 367 fichiers ont ici un code identique a l octet
pres, et sa javadoc contractee sert de reference de redaction. Mais elle ne porte AUCUN renvoi : sa
rupture les a tous retires, parce qu ils n ouvraient plus rien la-bas. Mesure : reprendre cette
javadoc en bloc effacerait 1 572 de ces renvois, qui ouvrent ici de vraies issues. C est ce garde
qui rend la reference utilisable sans danger.

**La polarite est celle d un PLANCHER, pas d un cliquet.** Un cliquet compte ce qu on tolere et doit
descendre ; celui-ci compte ce qu on possede et doit monter. `_commun.rapporte_plancher` porte cette
inversion, et le champ de l en-tete s appelle `floor` pour que le sens se voie sans lire le script.

**Ce qu il ne lit pas, et pourquoi.**

- **Le compte est global, et par renvoi DISTINCT.** Un fichier qui cite `#3068` deux fois compte
  pour un : ce qui se perd, c est qu un fichier cesse d ouvrir une discussion, pas qu il l ouvre une
  fois au lieu de deux. La premiere version comptait les occurrences, et sa premiere application l a
  refutee - une reecriture qui gardait le renvoi mais supprimait son doublon la faisait rougir sans
  que rien ne soit perdu (#4398).
- **Le total est global, pas fige par fichier.** Figer 983 valeurs et les tenir a chaque edition
  legitime ferait payer la discipline au mauvais endroit. Il s ensuit que dix renvois perdus dans un
  fichier, compenses par dix ajoutes dans un autre, echapperaient. Ce n est pas la menace : la
  menace est la perte EN MASSE, celle qu une reprise de tranche produit, et un plancher globale la
  voit au premier passage.
- **`src/test/java` est hors champ**, comme pour le cliquet de l article A30 auquel ce garde
  s adosse. La javadoc de test n a pas encore de decision.
- **Seules les lignes `///` comptent.** Un renvoi dans un commentaire d implementation releve du code
  et non du contrat ; le deplacer d un `///` vers un `//` est une decision de redaction, pas une
  perte, mais ce garde la comptera comme telle. La contrepartie est assumee : elle pousse a garder le
  renvoi la ou le lecteur du contrat le trouve.

**La cecite declaree.** Le motif est `#` suivi d au plus cinq chiffres, borne a droite. La borne
haute n est pas theorique : sans elle, `#4a90d9` - une couleur CSS citee dans un bloc - se comptait
comme un renvoi `#4`. Elle a ete posee apres l avoir mesuree. La borne basse est absente parce que
cinquante renvois du corpus tiennent en un ou deux chiffres - `#12`, `#28`, `#29`, `#33`, `#54` - et
ouvrent tous une vraie issue.

Ce que le garde ne saurait pas voir : un renvoi remplace par un autre, valide mais faux. Il compte,
il ne resout pas. La resolution est le travail de `verifie_okf.py` pour les ADR ; rien ne le fait
pour les issues, et une issue supprimee ne se distingue pas d une issue jamais ouverte.
"""

import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import PRODUCTION, TESTS, rapporte_plancher  # noqa: E402

RACINE = pathlib.Path(__file__).resolve().parents[2]

# DEUX planchers, un par arbre, et surtout pas un seul sur les deux (#4587).
#
# Un plancher unique laisserait une perte d un cote se compenser par un gain de l autre : total
# stable, verdict vert, et un renvoi perdu en production paye par un renvoi ajoute dans un test. Les
# deux populations sont donc DISJOINTES, chacune avec son ADR et son seuil, et la mutation de l une
# ne touche pas l autre - c est ce qui le prouve.
#
# Ce que l arbre de test apporte, mesure le 2026-08-28 : sur ses 990 renvois, 274 pointent vers une
# issue que la production ne cite NULLE PART. Les perdre ne renvoie pas le lecteur ailleurs, cela
# coupe le fil vers 151 discussions.
ZONES = {"4395": PRODUCTION.as_posix(), "4587": TESTS.as_posix()}

# Le plancher historique, celui de la production. Garde son nom : `renvois()` sans argument le sert.
ZONE = ZONES["4395"]

# `#` puis un a cinq chiffres, borne a droite. Voir la cecite declaree en tete.
RENVOI = re.compile(r"#\d{1,5}\b")


def fichiers(racine: pathlib.Path = None, zone: str = None) -> list[str]:
    """Les fichiers Java d une zone, relatifs a `racine`.

    Sur le depot, la liste vient de `git ls-files` : les fichiers SUIVIS. Sur une fixture, qui n est
    pas un depot, elle vient du parcours de l arbre. Sans cette seconde branche, `renvois(racine=...)`
    accepterait un chemin et lirait quand meme le depot reel, et son cas temoin passerait au vert sur
    une fixture qu il ne lit pas. Le defaut a deja ete commis une fois, sur le garde 4368.
    """
    racine = racine or RACINE
    zone = zone or ZONE
    if (racine / ".git").exists() or racine == RACINE:
        sortie = subprocess.run(
            ["git", "-C", str(racine), "ls-files", "-z", zone], capture_output=True, check=True
        ).stdout.decode()
        noms = [c for c in sortie.split("\0") if c]
    else:
        dossier = racine / zone
        noms = [str(f.relative_to(racine)) for f in sorted(dossier.rglob("*.java")) if f.is_file()]
    return sorted(n for n in noms if n.endswith(".java"))


def par_fichier(racine: pathlib.Path = None, zone: str = None) -> dict[str, int]:
    """Le nombre d issues DISTINCTES que la javadoc de chaque fichier cite.

    Distinctes, et non occurrences : un fichier qui cite `#3068` deux fois pointe vers une seule
    discussion, et supprimer le doublon ne retire rien a personne. Voir la cecite declaree en tete.
    """
    base = racine or RACINE
    comptes = {}
    for chemin in fichiers(base, zone):
        try:
            texte = (base / chemin).read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        vus = set()
        for ligne in texte.split("\n"):
            if ligne.strip().startswith("///"):
                vus.update(RENVOI.findall(ligne))
        if vus:
            comptes[chemin] = len(vus)
    return comptes


def renvois(racine: pathlib.Path = None, zone: str = None) -> int:
    """Le nombre d issues distinctes citees, somme sur les fichiers d une zone."""
    return sum(par_fichier(racine, zone).values())


def _auto_test() -> int:
    """Le garde voit-il une perte, et epargne-t-il ce qui n en est pas une ?

    Un plancher qui ne sait que reussir ne garde rien. Le cas temoin retire un renvoi d un bloc et
    exige que le compte baisse ; les autres cas tiennent les bords ou il se tromperait.
    """
    import tempfile

    def ecrire(racine: pathlib.Path, nom: str, contenu: str) -> None:
        p = racine / ZONE / nom
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(contenu, encoding="utf-8")

    cas = [
        (
            "un bloc qui cite deux issues",
            "/// Le decoupage suit #504, et le cas limite vient de #33.\nclass A {}\n",
            2,
        ),
        (
            "le renvoi retire du bloc : c est la perte que ce garde existe pour voir",
            "/// Le decoupage suit la regle, et le cas limite est traite.\nclass A {}\n",
            0,
        ),
        (
            "un renvoi en commentaire d implementation ne compte pas",
            "// vient de #504\nclass A {}\n",
            0,
        ),
        (
            "une couleur hexadecimale a six chiffres n est pas un renvoi",
            "/// La teinte de fond vaut #123456 dans la palette.\nclass A {}\n",
            0,
        ),
        (
            "un renvoi a un ou deux chiffres compte : cinquante du corpus en sont",
            "/// Le verrou de navigation vient de #54, la progression de #33.\nclass A {}\n",
            2,
        ),
        (
            "un lien javadoc vers un membre n est pas un renvoi",
            "/// Voir {@link Decoupage#applique} pour le detail.\nclass A {}\n",
            0,
        ),
        (
            "le meme renvoi deux fois dans un bloc compte pour un (#4398)",
            "/// Le delai fixe est remplace par une condition (#3068).\n"
            "/// C est assume (#3068), les tuiles etant une entree exterieure.\nclass A {}\n",
            1,
        ),
    ]

    echecs = 0
    for titre, contenu, attendu in cas:
        with tempfile.TemporaryDirectory() as d:
            racine = pathlib.Path(d)
            ecrire(racine, "A.java", contenu)
            obtenu = renvois(racine)
        marque = "ok  " if obtenu == attendu else "ECHEC"
        if obtenu != attendu:
            echecs += 1
        print(f"  {marque} {titre} : attendu {attendu}, obtenu {obtenu}")

    if echecs:
        print(f"\nÉCHEC : {echecs} cas sur {len(cas)}.", file=sys.stderr)
        return 1
    print(f"\nAuto-test concluant : le garde voit la perte, et epargne les {len(cas) - 2} bords.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    # Les deux planchers, l un apres l autre. Le code de sortie est le PIRE des deux : une perte
    # dans un arbre doit faire rougir, meme si l autre a gagne. C est la disjonction en pratique.
    codes = [
        rapporte_plancher("4395", "issues citees par la javadoc de production", renvois(), "renvois"),
        rapporte_plancher("4587", "issues citees par la javadoc de test",
                          renvois(zone=ZONES["4587"]), "renvois"),
    ]
    sys.exit(max(codes))
