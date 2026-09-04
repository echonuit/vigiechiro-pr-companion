#!/usr/bin/env python3
"""ADR 0010 - Les dialogues bloquants sont des ports injectables.

Pourquoi « probable » et non « certaine » : distinguer l'ADAPTATEUR d'un port (qui a le droit, et le
devoir, d'appeler `Alert`) d'un appel direct depuis une vue demande de savoir ce que la classe est.
Le paquet `commun/view` héberge les implémentations des ports (`Notificateur`, `Confirmateur`,
`NotificationDialogue`, `ConfirmationNavigation`, `ChoixDansListe`, `ChoixParBoutons`,
`DemandeurDeChoix`, `SelecteurFichier`) : leurs appels sont légitimes. Ailleurs, l'appel est un
suspect - pas une faute prouvée, car un outil de capture ou une amorce d'application peut avoir une
raison.

Ce que la règle protège : un `Alert.showAndWait()` en dur fige les tests TestFX headless (le dialogue
attend un clic qui ne viendra jamais) et rend la vue intestable sans écran.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import PRODUCTION, RACINES, rapporte, sans_commentaires_java, sort_si_contrat_demande

# Les DEUX arbres (#4462). Aucune decision n avait restreint ce garde a la production : il est ne
# avant que la question ne se pose. La dette de qualite ne connait pas de code de seconde zone, et
# la mesure d ouverture a rendu ZERO suspect dans l arbre de test - l extension ne coute donc rien
# et ferme la question pour de bon, la ou la laisser ouverte laissait un angle mort grandir.
SOURCES = PRODUCTION

# Le paquet des adaptateurs de ports : c'est leur rôle d'ouvrir un dialogue.
ADAPTATEURS = "fr/univ_amu/iut/commun/view/"

APPEL = re.compile(r"new Alert\(|\.showAndWait\(\)")


def fichiers(sources: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, extraites pour que `lus` les compte (issue #5015).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu il RETENAIT : un ciblage
    manque donnait zero suspect sur zero fichier, et ce zero passait pour un succes.
    """
    arbres = [sources] if sources else list(RACINES)
    return sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.java"))


def suspects(sources: pathlib.Path | None = None) -> list[str]:
    # Les commentaires sont retirés d'abord : un `///` qui CITE `Alert.showAndWait()` en expliquant un
    # bug passé n'est pas un appel (faux positif trouvé en clôture). Retrait mutualisé dans _commun.
    trouves = []
    for source in fichiers(sources):
        if ADAPTATEURS in source.as_posix():
            continue
        lignes = sans_commentaires_java(source.read_text(encoding="utf-8")).splitlines()
        for numero, ligne in enumerate(lignes, 1):
            if APPEL.search(ligne):
                trouves.append(f"{source}:{numero}  {ligne.strip()[:90]}")
    return trouves


CONTRAT = {
    "geste": "dialogue bloquant appele hors du port",
    "population": "PRODUCTION + TESTS",
    "dispositif": "cliquet",
    "seuil": "4, polarite=descend",
    "temoin": "scripts/adr/verifie_scripts.py#test_0010_dialogue_hors_port",
    "decision": "ADR 0010",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(
        rapporte("0010", "dialogue bloquant appelé hors du port", suspects(), lus=len(fichiers()))
    )
