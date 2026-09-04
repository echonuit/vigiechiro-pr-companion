#!/usr/bin/env python3
"""La preuve que la suite passe sous Windows et macOS est-elle encore FRAICHE ? (#3526, porté du bash).

## Pourquoi une veille en plus du job programme

Le job tourne le mardi. Mais un `schedule` GitHub peut etre **retarde**, **saute sous charge**, et il
est **desactive automatiquement** apres soixante jours sans activite sur le depot. Sans veille, son
silence se confondrait avec son succes - et le train de publication du mercredi partirait sur une
preuve qui n existe plus. C est l ADR 2748 : un dispositif qui peut ne rien verifier doit le dire.

## Il ne persiste rien, et c est le point

L historique des executions **est** la date recherchee. Un fichier commite ajouterait un commit de CI
par passage, un artefact expire a 90 jours et un cache a 7 - chacun deviendrait une seconde verite a
tenir a jour.

## Entree

Sur l entree standard, une ligne par execution, en TSV, de la plus recente a la plus ancienne :

    dateISO<TAB>conclusion<TAB>titre du run

Seules les executions **completes** comptent. Une execution peut etre **ciblee** depuis #3754 - deux
classes au lieu de 4600 -, et l API des runs ne dit pas quelles entrees ont ete passees a un
`workflow_dispatch`. La compter certifierait la fraicheur de la suite entiere sur la preuve de deux
classes. Vu en branchant la veille sur les vraies donnees du depot, ou elle a d abord valide un
passage cible de trois classes.

Le workflow porte donc son perimetre dans le **titre du run**, lu ici en 3e colonne. Filtrer sur le
declencheur aurait ete plus simple et laissait le train **sans issue de secours** : si le mardi
rougit sur une instabilite, aucune preuve ne peut plus naitre avant le mardi suivant. Un passage
manuel *complet* vaut un passage programme ; c est le passage *cible* qui ne vaut rien.

Une execution **reelle et concluante**, ici, c est `success` et lui seul - a la difference de la
veille du contrat API, ou un `failure` prouve que le contrat a bien ete exerce. Le job de plateformes
**conclut** : il rougit quand la suite ne passe pas. Un `failure` y est donc l inverse d une preuve,
et le compter rendrait la veille verte au moment precis ou la suite est cassee.

Usage : ... | python3 .github/scripts/veille_plateformes.py [--jours-max N]
        python3 .github/scripts/veille_plateformes.py --auto-test
"""

from __future__ import annotations

import datetime as dt
import sys

# 10 jours = un passage hebdomadaire manque, plus la marge d un decalage de `schedule`. En deca,
# c est la vie normale ; au-dela, le train de mercredi s appreterait a publier sans preuve.
JOURS_MAX = 10

# Les marqueurs poses par `run-name:` dans suite-sous-windows-et-macos.yml. Les renommer d un seul
# cote casse la detection - et la veille le dit plutot que de conclure au hasard.
MARQUEUR_COMPLET = "[complet]"
MARQUEUR_CIBLE = "[ciblé]"


def horodatage(texte: str) -> int | None:
    """L instant en secondes, ou None si la date est illisible : on refuse plutot que deviner."""
    try:
        # `fromisoformat` lit le « Z » depuis 3.11, et le projet exige 3.12 : pas de repli a ecrire.
        instant = dt.datetime.fromisoformat(texte)
    except ValueError:
        return None
    if instant.tzinfo is None:
        instant = instant.replace(tzinfo=dt.UTC)
    return int(instant.timestamp())


def juger(historique: str, jours_max: int, maintenant: int) -> int:
    """Le verdict, et 0 (frais) ou 1 (a regarder)."""
    if not historique.strip():
        print("❌ Historique vide : aucune exécution examinée.")
        print(
            "   Ce n'est pas « la suite n'a jamais tourné », c'est « la question n'a pas été posée »."
        )
        print("   Regarder l'appel à l'API GitHub (droit actions:read ? workflow renommé ?).")
        return 1

    lignes = historique.splitlines()

    # Aucun titre marque du tout = le `run-name:` a ete renomme, ou tous les runs le precedent. On
    # refuse en disant que c est la VEILLE qui est en cause, plutot que d annoncer « jamais eprouve ».
    if not any(MARQUEUR_COMPLET in l or MARQUEUR_CIBLE in l for l in lignes):
        print(
            f"❌ Aucun run ne porte de marqueur de périmètre (« {MARQUEUR_COMPLET} » / « {MARQUEUR_CIBLE} »)."
        )
        print("   C'est la veille qui est en cause, pas la suite. Deux causes, dans cet ordre :")
        print(
            "   1. les exécutions examinées sont TOUTES antérieures à la pose du marqueur (#3526) -"
        )
        print(
            "      normal le temps qu'un premier passage marqué ait lieu, et ça se résout tout seul ;"
        )
        print("   2. le « run-name: » du workflow a été renommé sans reporter le nom ici.")
        print(
            "   Dans les deux cas on refuse : sans marqueur, un passage ciblé serait compté comme une"
        )
        print("   preuve complète.")
        return 1

    completes = []
    for ligne in lignes:
        champs = ligne.split("\t")
        if len(champs) >= 3 and champs[1] == "success" and champs[2].startswith(MARQUEUR_COMPLET):
            completes.append(champs[0])
    derniere = max(completes) if completes else ""

    if not derniere:
        examinees = sum(1 for l in lignes if l)
        print(f"❌ Aucune exécution COMPLÈTE et réussie parmi les {examinees} examinées.")
        print("   ⚠️ Les passages ciblés (#3754) ne comptent pas : quelques classes sur 4600.")
        print("   Relancer « Suite complète sous Windows et macOS » en laissant « classes » VIDE.")
        print(
            "   Une suite qui échoue à chaque passage n'est pas une preuve périmée, c'est une preuve"
        )
        print("   absente : le train ne doit pas partir davantage que si le job ne tournait plus.")
        return 1

    instant = horodatage(derniere)
    if instant is None:
        print(f"❌ Date illisible dans l'historique : « {derniere} ».")
        print(
            "   Un format inattendu se lirait « très ancien » ou « tout frais » selon le hasard du"
        )
        print("   calcul : on refuse plutôt que de deviner.")
        return 1
    age = (maintenant - instant) // 86400

    if age > jours_max:
        print(
            f"❌ Dernière preuve réelle il y a {age} jours ({derniere}), au-delà des {jours_max} tolérés."
        )
        print(
            "   Le `schedule` du mardi a peut-être été sauté, retardé, ou désactivé après soixante"
        )
        print(
            "   jours sans activité sur le dépôt. Relancer « Suite complète sous Windows et macOS »."
        )
        return 1

    print(f"✔ Suite éprouvée sous Windows et macOS il y a {age} jour(s) ({derniere}).")
    return 0


C = "[complet] toute la suite"
K = "[ciblé] CartesAccueilTest"

# (attendu, libelle, historique, motif exige dans la sortie). Le motif n est pas decoratif : sans
# lui, un cas ne juge que le code, et deux refus pour des raisons opposees se ressemblent trait pour
# trait. Vu en eprouvant cet auto-test : en retirant la garde du marqueur absent, AUCUN cas n a
# rougi, parce que le refus tombait quand meme, en accusant la suite au lieu de la veille.
CAS = (
    (0, "une preuve d'hier est fraîche", f"2026-08-13T06:00:00Z\tsuccess\t{C}", ""),
    (1, "une preuve de trois semaines ne l'est plus", f"2026-07-24T06:00:00Z\tsuccess\t{C}", ""),
    # Les refus explicites, ceux qu on oublie : sans eux, la veille rendrait un « 0 jour » rassurant
    # la ou elle ne sait rien. Une mesure vide n est pas un zero.
    (1, "un historique vide est un refus, pas un zéro", "", "Historique vide"),
    (
        1,
        "aucune exécution réussie est un refus",
        f"2026-08-13T06:00:00Z\tfailure\t{C}\n2026-08-06T06:00:00Z\tfailure\t{C}",
        "",
    ),
    # Le cas qui a motive le marqueur : un passage CIBLE, reussi et tout frais, ne prouve rien.
    (
        1,
        "un passage ciblé, même réussi et récent, n'est pas une preuve",
        f"2026-08-14T06:00:00Z\tsuccess\t{K}",
        "Aucune exécution COMPLÈTE",
    ),
    # L ISSUE DE SECOURS : un passage complet lance A LA MAIN vaut preuve. Sans lui, un mardi rouge
    # bloquerait le train une semaine.
    (
        0,
        "un passage complet lancé à la main vaut preuve",
        f"2026-08-13T18:00:00Z\tsuccess\t{C}",
        "",
    ),
    # Le marqueur a disparu : refuser en accusant la VEILLE, et surtout ne pas prendre le premier
    # succes venu - sans quoi le renommage ferait passer un cible pour un complet.
    (
        1,
        "un historique sans aucun marqueur accuse la veille",
        "2026-08-14T06:00:00Z\tsuccess\tSuite sous Windows et macOS",
        "C'est la veille qui est en cause",
    ),
    (1, "une date illisible est un refus", f"avant-hier\tsuccess\t{C}", ""),
    # Controle NEGATIF : un echec recent ne doit pas masquer une reussite recente.
    (
        0,
        "une réussite reste vue même si un échec la suit",
        f"2026-08-14T06:00:00Z\tfailure\t{C}\n2026-08-13T06:00:00Z\tsuccess\t{C}",
        "",
    ),
    # Controle NEGATIF : un cible recent ne masque pas un complet plus ancien mais encore frais.
    (
        0,
        "un ciblé récent ne masque pas un complet encore frais",
        f"2026-08-14T06:00:00Z\tsuccess\t{K}\n2026-08-12T06:00:00Z\tsuccess\t{C}",
        "",
    ),
)


def _auto_test() -> int:
    """Les dix cas de la version bash, chacun jugé sur son code ET sur son message."""
    import contextlib
    import io

    echecs = cas = rouges = 0
    maintenant = horodatage("2026-08-14T12:00:00Z")
    for attendu, libelle, historique, motif in CAS:
        cas += 1
        if attendu != 0:
            rouges += 1
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
            code = juger(historique, 10, maintenant)
        sortie = tampon.getvalue()
        if code != attendu:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {code}")
            echecs = 1
        elif motif and motif not in sortie:
            print(
                f"  ✘ {libelle} : code {code} attendu, mais le motif « {motif} » manque au verdict"
            )
            echecs = 1
        else:
            print(f"  ✔ {libelle}")

    print()
    verbe = "DOIT" if rouges == 1 else "DOIVENT"
    print(f"{cas} cas, dont {rouges} qui {verbe} rougir.")
    return echecs


if __name__ == "__main__":
    jours_max = JOURS_MAX
    arguments = sys.argv[1:]
    if "--auto-test" in arguments:
        sys.exit(_auto_test())
    while arguments:
        if arguments[0] == "--jours-max":
            jours_max = int(arguments[1])
            arguments = arguments[2:]
        else:
            print(f"Option inconnue : {arguments[0]}", file=sys.stderr)
            sys.exit(2)
    sys.exit(juger(sys.stdin.read(), jours_max, int(dt.datetime.now(dt.UTC).timestamp())))
