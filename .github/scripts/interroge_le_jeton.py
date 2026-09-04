#!/usr/bin/env python3
"""Un appel a la plateforme Vigie-Chiro, et son code HTTP. Rien d autre. (#4385, porte du bash.)

## Pourquoi ce script existe

Trois endroits du depot appelaient la plateforme et normalisaient son code, avec la meme ligne
recopiee : le controle du jeton du tournage, sa revocation, et le pas « Jeton valide ? »
d `api-live.yml`. Deux d entre eux portaient meme une fonction du meme nom, ecrites a trois heures
d intervalle.

C est ainsi que le defaut du « HTTP 000000 » s est retrouve aux trois : `curl -w '%{http_code}'`
ecrit DEJA « 000 » quand la connexion echoue ET sort non nul, si bien que le `|| echo 000` habituel
en ajoutait un second (#4328). Le classement restait juste, le nombre affiche non - et c est le genre
de ligne qui fait douter de tout le reste au moment ou on la lit.

## Ce qu il ne fait pas, et c est le point

**Il ne juge pas.** Il rend un code sur la sortie standard et sort 0, meme sur `000`. Le verdict
appartient a l appelant, parce que les appelants n ont pas le meme :

- `api-live.yml` : un jeton mort AVERTIT et le run reste vert. Sans plateforme, la suite de contrat
  n a rien a dire, et la faire rougir ferait lire une derive de l API a chaque expiration de secret.
- `verifie_jeton_vivant.py` : un jeton mort REFUSE. Filmer quand meme donnerait un ecran hors ligne
  parfaitement convaincant et muet sur son propre objet (ADR 4142).
- `revoque_jeton.py` : un `401` est un SUCCES. Le but n etait pas « le serveur a repondu 200 » mais
  « ce jeton ne sert plus a personne ».

Trois lectures du meme chiffre, dont deux opposees. Les faire converger « pour la coherence »
casserait l une des trois en silence : c est pourquoi seule la MESURE descend ici, et pas le verdict.

## Ce qu il garantit

- `-o /dev/null` : le corps ne nous apprend rien de plus que le code, et l imprimer ferait passer la
  reponse d une erreur - ou l identite du compte - dans un journal public.
- le repli sur `000` remplace le code, il ne s y ajoute pas.
- Une sortie vide vaut `000` : un code manquant n est pas un code.

## Il s eprouve sans reseau, et cette phrase est devenue vraie avec le portage

La version bash figeait `BASE` au chargement, et son quatrieme cas posait `VIGIECHIRO_URL` en prefixe
d appel de fonction - ce qui ne reevalue rien. Ce cas-la interrogeait donc la plateforme REELLE a
chaque PR, alors que l en-tete annonçait « aucun reseau ». Mesure du 2026-09-04 : 0,24 s contre
0,022 s une fois la base vraiment posee sur le port mort, soit onze fois plus.

Le portage ne pouvait pas reproduire ce piege sans l ecrire expres, et l ecrire aurait demande de
garder une phrase fausse. La base se lit donc A L APPEL, et les quatre cas visent le port mort.
C est la seule difference de comportement du portage, et elle est mesuree.

Usage : python3 .github/scripts/interroge_le_jeton.py [--auto-test]
        JETON=<jeton> [VIGIECHIRO_URL=<base>] python3 .github/scripts/interroge_le_jeton.py [chemin] [méthode]

  chemin   défaut `/moi`
  méthode  défaut `GET`
"""

from __future__ import annotations

import os
import subprocess
import sys

PLATEFORME = "https://vigiechiro.herokuapp.com/api/v1"


def base_de(base: str | None = None) -> str:
    """La base d appel, lue A L APPEL et non au chargement."""
    return base or os.environ.get("VIGIECHIRO_URL") or PLATEFORME


def interroger(chemin: str = "/moi", methode: str = "GET", base: str | None = None) -> str:
    """Le code HTTP, et rien d autre. `curl` reste l appelant : c est lui qui mesure."""
    rendu = subprocess.run(
        [
            "curl",
            "-s",
            "-o",
            "/dev/null",
            "-w",
            "%{http_code}",
            "-X",
            methode,
            "-u",
            f"{os.environ.get('JETON', '')}:",
            f"{base_de(base)}{chemin}",
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    # Le repli REMPLACE le code, il ne s y ajoute pas : c est tout le defaut du « 000000 ».
    if rendu.returncode != 0 or not rendu.stdout.strip():
        return "000"
    return rendu.stdout.strip()


PORT_MORT = "http://127.0.0.1:1"

CAS = (
    # Le cas qui a demasque le defaut d origine : un seul « 000 », pas deux colles.
    ("une lecture qui ne répond pas rend un code, et un seul", "/moi", "GET"),
    # La meme chose sur l ecriture : la revocation passe par un POST, et rien ne garantissait que la
    # normalisation valait pour les deux tant qu elles vivaient dans deux copies.
    ("une écriture qui ne répond pas rend un code, et un seul", "/logout", "POST"),
    # Et le defaut se voit aussi sur un chemin qui n existe pas : ce n est pas la route qui normalise.
    ("un chemin quelconque ne change rien à la normalisation", "/nimporte", "GET"),
)


def _auto_test() -> int:
    """Quatre cas contre un port mort, dont celui qui garde la promesse centrale."""
    total = echecs = 0
    print("AUTO-TEST")

    ancien = os.environ.get("JETON")
    os.environ["JETON"] = "zzz"
    try:
        for libelle, chemin, methode in CAS:
            total += 1
            obtenu = interroger(chemin, methode, PORT_MORT)
            if obtenu == "000":
                print(f"  [OK   ] {libelle:<58} -> {obtenu}")
            else:
                print(f"  [ÉCHEC] {libelle:<58} -> {obtenu} (attendu 000)")
                echecs += 1

        # Le cas qui garde la promesse centrale : elle rend un code, elle ne le JUGE pas. Sans lui,
        # une sonde devenue jugeante ne ferait rougir personne - et le `run:` d `api-live.yml` tourne
        # sous `bash -e`, donc son pas ECHOUERAIT sur un jeton expire, ce que son tri refuse.
        total += 1
        try:
            interroger("/moi", "GET", PORT_MORT)
            juge = False
        except SystemExit:
            juge = True
        libelle = "elle rend 0 même quand la plateforme ne répond pas"
        if not juge:
            print(f"  [OK   ] {libelle:<58} -> ne juge pas")
        else:
            print(f"  [ÉCHEC] {libelle:<58} -> ELLE JUGE")
            echecs += 1
    finally:
        if ancien is None:
            os.environ.pop("JETON", None)
        else:
            os.environ["JETON"] = ancien

    print()
    print(
        f"{total} cas, tous contre un port mort : aucun réseau, et le défaut du « 000000 » rougit."
    )
    if echecs != 0:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au code que ce script rend.")
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    print(
        interroger(
            sys.argv[1] if len(sys.argv) > 1 else "/moi",
            sys.argv[2] if len(sys.argv) > 2 else "GET",
        )
    )
