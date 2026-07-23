#!/usr/bin/env python3
"""ADR 0010 — Les dialogues bloquants sont des ports injectables.

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

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

SOURCES = pathlib.Path("src/main/java")

# Le paquet des adaptateurs de ports : c'est leur rôle d'ouvrir un dialogue.
ADAPTATEURS = "fr/univ_amu/iut/commun/view/"

APPEL = re.compile(r"new Alert\(|\.showAndWait\(\)")

BLOC = re.compile(r"/\*.*?\*/", re.S)
LIGNE = re.compile(r"//[^\n]*")


def sans_commentaires(source: str) -> str:
    """Neutralise les commentaires Java (bloc, ligne, doc `///`) en préservant les sauts de ligne,
    pour que les numéros restent justes. Sans cela, un commentaire qui CITE `Alert.showAndWait()` en
    expliquant un bug passé est compté comme un appel - le faux positif trouvé en clôture (un `///` de
    SiteDetailController gonflait le compte et le cliquet dès sa pose)."""
    sans_bloc = BLOC.sub(lambda m: re.sub(r"[^\n]", " ", m.group()), source)
    return LIGNE.sub("", sans_bloc)


def suspects() -> list[str]:
    trouves = []
    for source in sorted(SOURCES.rglob("*.java")):
        if ADAPTATEURS in source.as_posix():
            continue
        lignes = sans_commentaires(source.read_text(encoding="utf-8")).splitlines()
        for numero, ligne in enumerate(lignes, 1):
            if APPEL.search(ligne):
                trouves.append(f"{source}:{numero}  {ligne.strip()[:90]}")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte("0010", "dialogue bloquant appelé hors du port", suspects()))
