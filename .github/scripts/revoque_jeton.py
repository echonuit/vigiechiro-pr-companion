#!/usr/bin/env python3
"""Revocation du jeton d un tournage connecte (#4305, porte du bash.)

## Pourquoi revoquer, alors que le jeton expire tout seul

Un jeton Vigie-Chiro vit **quatorze jours**. Le tournage connecte, lui, produit une **image**, et une
image ne se masque pas : le masquage de GitHub ne couvre que les journaux. A terme ces clips seront
publies, et une fuite sur le tag d une version est definitive.

Revoquer a la fin du run ramene donc la fenetre d exposition de quatorze jours a la duree d un
tournage. C est la derniere barriere, celle qui tient encore quand les deux premieres - le jeton
n entre pas par l ecran, le secret ne depasse pas son pas - ont manque.

## Ce que la plateforme permet, et a quel prix

`POST /logout`, authentifie par le jeton lui-meme, fait un `$unset` de **ce jeton et de lui seul**.
Un compte porte une **carte** de jetons : ni le navigateur de qui a pose le secret, ni le jeton du
contrat hebdomadaire ne sont atteints. C est cette propriete, et elle seule, qui rend possible un
second secret sans rien couter a `api-live.yml`.

## La regle qu on inverse sans s en apercevoir

**`404` et `401` sont des SUCCES.** L objectif n est pas « le serveur a repondu 200 » mais « ce jeton
ne sert plus a personne » :

    200   le jeton a ete retire de la carte du compte
    404   il n y etait pas : deja mort, et le but est atteint
    401   l authentification a echoue : il ne valait deja plus rien
    ***   on ne sait pas, et c est le seul cas qui demande de parler

C est exactement le genre de regle qu une relecture distraite retourne, d ou l auto-test : le verdict
se prononce dans une fonction, et cette fonction passe par des codes connus a chaque execution.

**Et `verifie_jeton_vivant.py` lit la MEME table a l envers** (#4328). Chez lui, `401` est un refus :
il demande « ce jeton sert-il encore ? » AVANT de filmer. Ici on demande « ne sert-il vraiment
plus ? » APRES. Les harmoniser « pour la coherence » casserait l un des deux en silence.

## Il ne fait JAMAIS rougir le run

Le tournage, lui, a reussi. Un rouge ici ferait lire un echec pour une raison qui n est pas celle
qu on regarde. Mais se taire laisserait croire a une revocation qui n a pas eu lieu : le cas incertain
sort donc un **avertissement** qui nomme le code obtenu et dit comment revoquer a la main.

Usage : python3 .github/scripts/revoque_jeton.py [--auto-test]
        JETON=<jeton> python3 .github/scripts/revoque_jeton.py
"""

from __future__ import annotations

import os
import pathlib
import sys

ICI = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(ICI))

from interroge_le_jeton import base_de, interroger


def verdict(code: str) -> int:
    """0 quand le jeton est hors d usage, 1 quand on ne sait pas.

    Separe de l appel : c est la partie qui porte la regle, donc la seule qu on puisse eprouver sans
    reseau.
    """
    if code == "200":
        print("✓ Jeton révoqué : la plateforme l'a retiré de la carte du compte.")
        return 0
    if code in ("401", "404"):
        print(
            f"✓ Jeton déjà hors d'usage (HTTP {code}) : il n'était plus dans la carte, ou n'était plus accepté."
        )
        return 0
    print(f"⚠ Révocation INCERTAINE (HTTP {code}) : la plateforme n'a pas confirmé le retrait.")
    print("  Le jeton peut être encore vivant, et il le restera jusqu'à ses quatorze jours.")
    print("  Le révoquer à la main :")
    print(f"      curl -X POST -u '<le jeton>:' {base_de()}/logout")
    print("  puis en poser un frais : gh secret set VIGIECHIRO_TOKEN_TOURNAGE")
    return 1


PORT_MORT = "http://127.0.0.1:1"

CAS = (
    ("200", "sur", "200 : le serveur a retiré le jeton"),
    # Les deux cas qui font tout l interet de cet auto-test. Les lire comme des echecs ferait
    # avertir a chaque tournage dont le jeton avait deja expire, et on apprendrait a ignorer la ligne.
    ("404", "sur", "404 : le jeton n'était plus dans la carte, donc mort"),
    ("401", "sur", "401 : le jeton n'était plus accepté, donc mort"),
    # Et les controles negatifs, sans lesquels une fonction qui rendrait TOUJOURS 0 passerait.
    ("500", "incertain", "500 : la plateforme a fauté, on ne sait pas"),
    ("000", "incertain", "000 : injoignable, on ne sait pas"),
    ("302", "incertain", "302 : réponse inattendue, on ne sait pas"),
)


def _auto_test() -> int:
    """Six verdicts hors ligne, puis le chemin d appel vers la sonde partagee."""
    import contextlib
    import io

    total = echecs = surs = 0
    print("AUTO-TEST")
    for code, attendu, nom in CAS:
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            obtenu = "sur" if verdict(code) == 0 else "incertain"
        total += 1
        if attendu == "sur":
            surs += 1
        if obtenu == attendu:
            print(f"  [OK   ] {nom:<58} -> {obtenu}")
        else:
            print(f"  [ÉCHEC] {nom:<58} -> {obtenu} (attendu {attendu})")
            echecs += 1

    # Le cas qui exercait l APPEL vit desormais chez `interroge_le_jeton.py` (#4385). Celui-ci
    # verifie seulement que le chemin d appel de CE script y mene bien.
    total += 1
    ancien = os.environ.get("JETON")
    os.environ["JETON"] = "zzz"
    try:
        obtenu = interroger("/logout", "POST", PORT_MORT)
    finally:
        if ancien is None:
            os.environ.pop("JETON", None)
        else:
            os.environ["JETON"] = ancien
    libelle = "la sonde partagée rend bien un code, et un seul"
    if obtenu == "000":
        print(f"  [OK   ] {libelle:<58} -> {obtenu}")
    else:
        print(f"  [ÉCHEC] {libelle:<58} -> {obtenu} (attendu 000)")
        echecs += 1

    print()
    print(f"{total} cas, dont {surs} qui doivent conclure « hors d'usage ».")
    if echecs != 0:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce script.")
        return 1
    print("Auto-test concluant.")
    return 0


def _ajoute(variable: str, ligne: str) -> None:
    chemin = os.environ.get(variable)
    if chemin:
        with open(chemin, "a", encoding="utf-8") as f:
            f.write(ligne + "\n")


def revoquer() -> int:
    """La revocation reelle. TOUJOURS 0 : le tournage, lui, a reussi."""
    if not os.environ.get("JETON"):
        print("Aucun jeton à révoquer : la variable JETON est vide.")
        print("Ce n'est pas une anomalie si le tournage n'était pas connecté.")
        return 0

    code = interroger("/logout", "POST")

    import contextlib
    import io

    tampon = io.StringIO()
    with contextlib.redirect_stdout(tampon):
        etat = verdict(code)
    sortie = tampon.getvalue().rstrip("\n")
    print(sortie)
    _ajoute("GITHUB_STEP_SUMMARY", sortie)

    # Le verdict sort AUSSI en sortie de pas, parce que quelqu un en depend : le versement des clips
    # connectes n a lieu que si le retrait est CONFIRME. Depuis #4324 le clip montre le jeton - un
    # jeton mort n est pas un secret, mais « il est mort » doit etre un fait, pas un espoir.
    _ajoute("GITHUB_OUTPUT", "revoque=oui" if etat == 0 else "revoque=incertain")

    if etat != 0:
        print(
            f"::warning::Révocation incertaine (HTTP {code}) : révoquer le jeton à la main, "
            "cf. le résumé du run."
        )
    # Toujours 0. L incertitude est portee par l avertissement, pas par le code de sortie.
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(revoquer())
