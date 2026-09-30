#!/usr/bin/env python3
"""Surveille une demande, et DIT quand il n a pas pu lire au lieu de sortir en 0 (#5621).

    python3 .github/scripts/surveille_la_demande.py <depot> <numero> [budget-en-secondes]
    python3 .github/scripts/surveille_la_demande.py --auto-test

## Le defaut que ce script existe pour ne plus reecrire

`ouvrir-une-pr` prescrit de sortir en `0` quel que soit le verdict, et elle a raison pour ce qu elle
vise : `gh pr checks` sort NON NUL des qu une verification echoue, donc un moniteur qui s arrete sur
ce code meurt exactement quand il servait.

Mais la regle ne distingue pas **j ai lu un verdict** de **je n ai pas pu lire**. Deux moniteurs
ecrits a la main le 2026-09-30, par deux sessions qui ne se parlaient pas, portaient chacun une moitie
du defaut :

    le mien        sortait en 0 sur une panne reseau, et son journal se terminait
                   comme une surveillance conclue - la demande etait verte, 26 SUCCESS,
                   et je ne le savais pas par lui
    celui du pair  rendait [] sur un echec reseau, donc un compte de zero, donc
                   « toujours en cours apres 27 min » - pas de faux termine, mais
                   « je n ai pas pu lire » s y lisait « ca tourne encore »

Leur protection etait ACCIDENTELLE et ils l ecrivaient : le garde `-gt 0` etait la pour ne pas
conclure sur une liste vide au demarrage, avant que la forge ait cree les check-runs. Qu il attrape
aussi la panne reseau etait une coincidence, et une pratique qu on ne s est pas formulee ne se rejoue
pas a volonte.

## Trois issues, donc trois codes

    0   CONCLU           toutes les verifications ont rendu un verdict, QUELLE QUE SOIT leur couleur
    3   PAS CONCLU       le budget est epuise et il en restait en cours
    4   PAS PU LIRE      trois lectures de suite n ont rien rendu, ou `gh` est absent

Un rouge sort donc en **0** : c est un verdict, et l intention d origine tient. Ne pas avoir lu n est
pas un verdict, et c est la seule chose que ce script ajoute a la regle.

## Une liste VIDE n est pas une panne

La forge rend `[]` legitimement tant qu elle n a pas cree les check-runs. C est une lecture REUSSIE
qui dit « rien encore », donc `en cours`, et surtout pas `illisible`. C est la distinction que le
`-gt 0` couvrait sans le dire, et la confondre ferait abandonner la surveillance a la seconde ou elle
commence.

## Ce qu il ne fait pas

**Il ne juge pas la couleur.** Conclure que la demande est verte, et decider de fusionner, reste le
travail de qui lit. Ce script repond a « les verifications ont-elles fini » et a rien d autre.

**Il ne reprend pas indefiniment.** Trois tentatives par lecture, comme
`mesure_duree_portail.insiste` : au-dela, une API qui bafouille trois fois de suite n est plus un
hoquet, et le dire vaut mieux que d attendre.
"""

from __future__ import annotations

import json
import os
import pathlib
import subprocess
import sys
import time

CONCLU = 0
PAS_CONCLU = 3
PAS_PU_LIRE = 4

BUDGET_PAR_DEFAUT = 1800
PAUSE_ENTRE_SONDES = 30
PAUSE_DE_REPRISE = 3
TENTATIVES = 3

# ⟨les etats que la forge rend⟩ `gh pr checks --json bucket` range chaque verification dans l un de
# ces cinq. Seul `pending` n est pas un verdict ; `skipping` en est un, et le compter comme un echec
# ferait crier sur des demandes saines - c est le second piege que `ouvrir-une-pr` nomme deja.
EN_ATTENTE = "pending"


def interroge(depot: str, numero: int, lanceur=subprocess.run, dors=time.sleep) -> list | None:
    """Les verifications de la demande, ou `None` quand on n a PAS PU LIRE.

    Le `lanceur` est une couture : sans elle, les cas de ce script exigeraient le reseau, et un
    dispositif dont les cas ne tournent pas hors ligne ne se relance jamais.
    """
    for essai in range(1, TENTATIVES + 1):
        # ⟨`gh` ABSENT leve, et `check=False` ne le couvre pas⟩ `check=False` parle du code de sortie ;
        # un executable introuvable fait lever `subprocess.run` avant qu il y ait un code. Le meme
        # defaut vivait dans `mesure_duree_portail` et `mesure_minutes_par_pr`, repare dans les deux,
        # et il reste dans six appels du depot (#5692).
        try:
            rendu = lanceur(
                ["gh", "pr", "checks", str(numero), "--repo", depot, "--json", "name,bucket"],
                capture_output=True,
                text=True,
                check=False,
            )
        except OSError:
            return None
        # ⟨on juge sur le CONTENU, jamais sur le code⟩ `gh pr checks` sort non nul des qu une
        # verification echoue, ET rend son JSON. Lire le code ici ferait prendre une demande rouge
        # pour une panne de lecture, ce qui est exactement le defaut a l envers.
        if rendu.stdout.strip():
            try:
                return json.loads(rendu.stdout)
            except json.JSONDecodeError:
                pass
        if essai < TENTATIVES:
            dors(PAUSE_DE_REPRISE)
    return None


def en_cours(checks: list) -> int:
    """Combien n ont pas encore rendu de verdict. Une liste vide en rend zero, et c est voulu."""
    return sum(1 for c in checks if c.get("bucket") == EN_ATTENTE)


def composition(checks: list) -> str:
    """Le compte par etat, pour que le journal dise ce qui a ete lu et pas seulement combien."""
    par_etat: dict[str, int] = {}
    for c in checks:
        par_etat[c.get("bucket", "?")] = par_etat.get(c.get("bucket", "?"), 0) + 1
    return ", ".join(f"{n} {etat}" for etat, n in sorted(par_etat.items())) or "aucune"


def surveille(
    depot: str,
    numero: int,
    budget: int = BUDGET_PAR_DEFAUT,
    pause: int = PAUSE_ENTRE_SONDES,
    lire=None,
    dors=time.sleep,
) -> int:
    """Sonde jusqu a conclusion, epuisement du budget, ou echec de lecture."""
    lire = lire or (lambda: interroge(depot, numero))
    ecoule = 0
    while True:
        checks = lire()
        if checks is None:
            print(
                f"JE N AI PAS CONCLU : {TENTATIVES} lectures de suite n ont rien rendu "
                f"(« gh » absent, ou la forge muette). Le verdict de #{numero} reste inconnu.",
                file=sys.stderr,
            )
            return PAS_PU_LIRE

        restantes = en_cours(checks)
        # ⟨`flush` n est pas un ornement⟩ Python tamponne `stdout` des qu il n est pas un
        # terminal, et un moniteur redirige toujours. Sans lui, les lignes de progression
        # n arrivent qu a la SORTIE du processus : le journal reste vide pendant toute la
        # surveillance, ce qui se lit « il ne se passe rien ». Vu sur la demande de ce lot meme.
        print(
            f"[{ecoule:5d}s] {len(checks)} verification(s) : {composition(checks)}",
            flush=True,
        )

        # ⟨une liste VIDE n est pas une conclusion⟩ Sans cette moitie, une demande dont la forge n a
        # pas encore cree les check-runs serait declaree conclue a la premiere sonde.
        if checks and restantes == 0:
            print(f"CONCLU : {composition(checks)}", flush=True)
            return CONCLU

        if ecoule + pause > budget:
            quoi = f"{restantes} en cours" if checks else "aucune verification creee"
            print(
                f"JE N AI PAS CONCLU : budget de {budget} s epuise, {quoi}. "
                f"Le verdict de #{numero} reste inconnu.",
                file=sys.stderr,
            )
            return PAS_CONCLU

        dors(pause)
        ecoule += pause


def _serie(reponses: list) -> object:
    """Un lecteur qui rend les reponses donnees, puis redonne la derniere. Pour les cas."""
    restantes = list(reponses)

    def lire():
        return restantes.pop(0) if len(restantes) > 1 else restantes[0]

    return lire


def _auto_test() -> int:
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "scripts"))
    from _commun import cas_d_auto_test

    verifie, echecs = cas_d_auto_test()

    VERT = [{"name": "build", "bucket": "pass"}]
    ROUGE = [{"name": "build", "bucket": "fail"}, {"name": "lint", "bucket": "pass"}]
    ENCOURS = [{"name": "build", "bucket": EN_ATTENTE}]

    muet = {"ecoule": 0}

    def dors(_):
        muet["ecoule"] += 1

    # ⟨LES TROIS ISSUES, une par code⟩ C est la partition entiere, et chacune doit etre atteinte par
    # un chemin different : sans les trois, ce script n aurait fait qu inverser le defaut d origine.
    verifie(
        "une demande conclue rend 0",
        lambda: surveille("d", 1, lire=_serie([VERT]), dors=dors),
        CONCLU,
    )
    verifie(
        "une demande ROUGE conclue rend 0 aussi : un rouge est un verdict",
        lambda: surveille("d", 1, lire=_serie([ROUGE]), dors=dors),
        CONCLU,
    )
    verifie(
        "ne pas avoir pu lire rend 4, et ne se confond avec aucun verdict",
        lambda: surveille("d", 1, lire=_serie([None]), dors=dors),
        PAS_PU_LIRE,
    )
    verifie(
        "un budget epuise sur une demande en cours rend 3",
        lambda: surveille("d", 1, budget=60, pause=30, lire=_serie([ENCOURS]), dors=dors),
        PAS_CONCLU,
    )

    # ⟨la liste VIDE, qui est le cas que la protection accidentelle couvrait⟩ Elle ne doit etre ni
    # une conclusion ni une panne : la forge n a pas encore cree les check-runs.
    verifie(
        "une liste vide au demarrage n est pas une conclusion",
        lambda: surveille("d", 1, budget=60, pause=30, lire=_serie([[]]), dors=dors),
        PAS_CONCLU,
    )
    verifie(
        "et elle n est pas une panne non plus : la sonde suivante conclut",
        lambda: surveille("d", 1, lire=_serie([[], VERT]), dors=dors),
        CONCLU,
    )

    # ⟨la lecture elle-meme, par une COUTURE et non par le reseau⟩ Les cas ci-dessus remplacent
    # `interroge` ; ceux-ci l exercent, sans quoi rien ne prouverait que ses branches repondent.
    class Rendu:
        def __init__(self, code, sortie):
            self.returncode, self.stdout, self.stderr = code, sortie, ""

    verifie(
        "gh NON NUL avec du JSON valide est une LECTURE, pas une panne",
        lambda: interroge("d", 1, lanceur=lambda *a, **k: Rendu(1, json.dumps(ROUGE))),
        ROUGE,
    )
    verifie(
        "gh qui rend du vide trois fois de suite est une panne",
        lambda: interroge("d", 1, lanceur=lambda *a, **k: Rendu(0, ""), dors=dors),
        None,
    )
    verifie(
        "gh qui rend [] est une lecture reussie, et rend []",
        lambda: interroge("d", 1, lanceur=lambda *a, **k: Rendu(0, "[]")),
        [],
    )

    # ⟨et le VRAI `subprocess`, sur un PATH vide⟩ Les coutures ci-dessus prouvent les branches ; elles
    # ne prouvent pas que `subprocess.run` leve bien ce que j attrape. Deux familles de cas, parce
    # qu un objet fabrique a la main ne prouve jamais la lecture de la vraie chose.
    chemin = os.environ.get("PATH", "")
    try:
        os.environ["PATH"] = str(pathlib.Path(__file__).resolve().parent / "aucun-outil-ici")
        verifie(
            "« gh » absent du PATH rend None, et ne leve pas",
            lambda: interroge("d", 1, dors=dors),
            None,
        )
    finally:
        os.environ["PATH"] = chemin

    return echecs()


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    if len(sys.argv) < 3:
        print(__doc__.strip().splitlines()[2].strip(), file=sys.stderr)
        raise SystemExit(2)
    sys.exit(
        surveille(
            sys.argv[1],
            int(sys.argv[2]),
            int(sys.argv[3]) if len(sys.argv) > 3 else BUDGET_PAR_DEFAUT,
        )
    )
