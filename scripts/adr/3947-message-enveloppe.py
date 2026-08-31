#!/usr/bin/env python3
"""ADR 3947 - Un message montré à l'utilisateur se compose par CauseLisible, jamais à la main.

Pourquoi « probable » et non « certaine » : un `getMessage()` n'est fautif que si son résultat **atteint
l'utilisateur**. Le même appel dans un `LOG.fine`, dans un refus métier (`RegleMetierException`, dont le
message est écrit par nous et jamais nul), ou dans une chaîne assemblée pour un fichier CSV est
légitime. Aucun motif ne sait faire cette différence : c'est un humain qui tranche, script en main.

Le compte porte donc sur les **trois formes** que l'ADR 3470 nomme, et elles seules. Ce sont celles qui
produisent mécaniquement ce que la règle interdit, quel que soit le contexte :

1. le repli sur `toString()` - montre `java.lang.XxxException` quand le message est nul ;
2. le repli sur le nom de classe - même effet, en plus court ;
3. le déroulement d'**un seul** cran - une enveloppe peut en emballer une autre.

Ce que ce cliquet ne peut pas voir, et qu'il faut savoir en le lisant : un
`"Échec : " + echec.getMessage()` **nu** est tout aussi fautif, et il ne correspond à aucune des trois
formes. Il y en a des dizaines dans le dépôt, dont la plupart sont des refus métier légitimes. Compter
la forme nue rendrait un chiffre que personne ne saurait faire descendre, c'est-à-dire un cliquet qu'on
apprend à ignorer. Ce script compte ce qui est **décidable** ; le reste relève de la relecture.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import PRODUCTION, RACINES, rapporte, sans_commentaires_java  # noqa: E402

# Les DEUX arbres (#4462). Aucune decision n avait restreint ce garde a la production : il est ne
# avant que la question ne se pose. La dette de qualite ne connait pas de code de seconde zone, et
# la mesure d ouverture a rendu ZERO suspect dans l arbre de test - l extension ne coute donc rien
# et ferme la question pour de bon, la ou la laisser ouverte laissait un angle mort grandir.
SOURCES = PRODUCTION

# 1. `x.getMessage() != null ? x.getMessage() : x.toString()` et sa variante inversée.
REPLI_TOSTRING = re.compile(r"getMessage\(\)\s*[!=]=\s*null\s*\?[^;]*?toString\(\)")

# 2. `x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage()`.
REPLI_NOM_DE_CLASSE = re.compile(r"getMessage\(\)\s*[!=]=\s*null\s*\?[^;]*?getSimpleName\(\)")

# 3. `x.getCause() != null ? x.getCause().getMessage() : x.getMessage()`.
UN_SEUL_CRAN = re.compile(r"getCause\(\)\s*[!=]=\s*null\s*\?[^;]*?getMessage\(\)")

FORMES = (
    (REPLI_TOSTRING, "repli sur toString()"),
    (REPLI_NOM_DE_CLASSE, "repli sur le nom de classe"),
    (UN_SEUL_CRAN, "déroulement d'un seul cran"),
)


def suspects(sources: pathlib.Path | None = None) -> list[str]:
    trouves = []
    arbres = [sources] if sources else list(RACINES)
    for fichier in sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.java")):
        # CauseLisible est le remède : elle porte ces motifs dans sa propre documentation, et se
        # compterait elle-même. C'est le défaut de l'ADR 3645 - un détecteur textuel s'exclut de son
        # corpus - et il a déjà coûté un cliquet qui certifiait ses propres débiteurs.
        if fichier.name == "CauseLisible.java":
            continue
        contenu = sans_commentaires_java(fichier.read_text(encoding="utf-8"))
        # Les formes tiennent souvent sur DEUX lignes : `spotless` coupe volontiers juste après le
        # `?`. Chercher ligne à ligne en manquerait la moitié, et un cliquet qui rend un chiffre trop
        # bas est pire qu'un cliquet absent : il se lit comme une dette maîtrisée. On cherche donc sur
        # le texte entier, et on retrouve la ligne en comptant les sauts qui précèdent l'occurrence.
        for motif, nom in FORMES:
            for occurrence in motif.finditer(contenu):
                numero = contenu.count("\n", 0, occurrence.start()) + 1
                trouves.append(f"{fichier}:{numero}  {nom}")
    return trouves


if __name__ == "__main__":
    sys.exit(
        rapporte(
            "3947", "message d'erreur composé à la main plutôt que par CauseLisible", suspects()
        )
    )
