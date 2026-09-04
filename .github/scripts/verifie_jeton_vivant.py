#!/usr/bin/env python3
"""Le jeton du tournage connecte est-il encore vivant ? (#4328, porte du bash.)

## Le defaut : « present » et « valide » ne sont pas le meme fait

Le tournage connecte REVOQUE son jeton en fin de run (#4305). Le secret, lui, reste pose. Apres un
tournage, `VIGIECHIRO_TOKEN_TOURNAGE` a donc l air parfaitement valide et ne vaut plus rien.

Le pas de controle verifiait que le secret etait non vide, ce qu il est. Le tournage partait, le banc
deposait le jeton mort, la modale le soumettait, et la plateforme repondait `401`. Un scenario
rougissait alors sur « le libelle d identite est vide » : le symptome a trois pas de la cause.

Et le cout n est PAS le temps perdu : mesure sur le run 32696587259, tire expres avec un jeton
revoque, tout tenait en 2 min 14 s. Le cout est que ce run finissait **VERT** - le tournage tourne
sous `failure.ignore` - et versait son clip hors ligne comme s il montrait la plateforme.

## Trois verdicts, et pas deux

C est le fond de l affaire. « Pas 200 » recouvre deux choses qu il ne faut pas confondre :

    200          le jeton vit. Le tournage part.
    401          LE JETON EST MORT. En poser un frais, et la commande est donnee.
    000 5xx *    LA PLATEFORME NE REPOND PAS. Poser un jeton frais n y changerait rien.

Les fondre referait, un cran plus loin, le defaut qu on corrige : on irait frapper un jeton pendant
une panne d Heroku, et on chercherait pourquoi le neuf ne marche pas mieux que l ancien.

## La meme table que `revoque_jeton.py`, lue a l ENVERS

La-bas, `401` est un **succes** : le jeton ne vaut plus rien, c etait le but. Ici c est un **refus**.
Deux scripts voisins qui traitent le meme code de facon opposee finissent par s harmoniser un jour
par erreur, « pour la coherence ». Ils ne parlent pas du meme moment : l un demande « ce jeton
sert-il encore ? » avant de filmer, l autre « ne sert-il vraiment plus ? » apres.

## Le refus est DUR, contrairement a `api-live.yml`

Chez lui, un jeton mort avertit et le run reste vert. Ici, filmer quand meme donnerait un ecran hors
ligne parfaitement convaincant et **muet sur son propre objet** (ADR 4142) : on le regarderait en
croyant savoir.

Usage : python3 .github/scripts/verifie_jeton_vivant.py [--auto-test]
        JETON=<jeton> python3 .github/scripts/verifie_jeton_vivant.py
"""

from __future__ import annotations

import os
import pathlib
import sys

ICI = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(ICI))

from interroge_le_jeton import base_de, interroger


def verdict(code: str) -> int:
    """0 le jeton vit, 1 il est mort, 2 la plateforme n a pas repondu.

    Separe de l appel : c est la partie qui porte la regle, donc la seule qu on puisse eprouver sans
    reseau.
    """
    if code == "200":
        print(
            "✓ Jeton vivant : la plateforme le reconnaît. Le tournage parlera à la plateforme réelle, en lecture."
        )
        return 0
    if code == "401":
        print(
            "✗ LA PLATEFORME REFUSE CE JETON (HTTP 401) : il est expiré, ou révoqué par un tournage précédent."
        )
        print(
            "  Le secret est bien posé - c'est sa VALEUR qui ne vaut plus rien, et rien ne le montrait."
        )
        print("  En poser un frais :")
        print("      gh secret set VIGIECHIRO_TOKEN_TOURNAGE")
        print(
            "  (jeton Vigie-Chiro, quatorze jours, révoqué en fin de run : c'est normal de recommencer.)"
        )
        return 1
    print(f"✗ LA PLATEFORME NE RÉPOND PAS (HTTP {code}) : ce n'est PAS le jeton qui est en cause.")
    print(
        f"  Poser un jeton frais n'y changerait rien. Vérifier que {base_de()} répond, puis relancer."
    )
    return 2


PORT_MORT = "http://127.0.0.1:1"

CAS = (
    ("200", "vivant", "200 : la plateforme reconnaît le jeton"),
    # Le cas de l issue. Un controle qui ne regardait que la presence du secret le laissait passer.
    ("401", "mort", "401 : jeton expiré ou révoqué par le tournage précédent"),
    # Et les trois qui separent les deux causes. Sans eux, un verdict qui rendrait « mort » pour tout
    # ce qui n est pas 200 passerait, et on irait frapper un jeton pendant une panne.
    ("000", "muette", "000 : injoignable, ce n'est pas le jeton"),
    ("503", "muette", "503 : la plateforme est en panne, ce n'est pas le jeton"),
    ("500", "muette", "500 : la plateforme a fauté, ce n'est pas le jeton"),
    # Le controle negatif de l autre bord : « refuse » ne vaut pas « n importe quel refus ».
    ("403", "muette", "403 : refus d'une autre nature, on ne conclut pas sur le jeton"),
)


def _auto_test() -> int:
    """Six verdicts hors ligne, puis la sonde partagee contre un port mort."""
    import contextlib
    import io

    total = echecs = 0
    print("AUTO-TEST")
    for code, attendu, nom in CAS:
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            etat = verdict(code)
        obtenu = {0: "vivant", 1: "mort"}.get(etat, "muette")
        total += 1
        if obtenu == attendu:
            print(f"  [OK   ] {nom:<62} -> {obtenu}")
        else:
            print(f"  [ÉCHEC] {nom:<62} -> {obtenu} (attendu {attendu})")
            echecs += 1

    # Le cas qui exercait l APPEL vit desormais chez `interroge_le_jeton.py`, avec ceux des deux
    # autres appelants : c est la qu est le code partage, donc la qu il doit rougir (#4385).
    total += 1
    ancien = os.environ.get("JETON")
    os.environ["JETON"] = "zzz"
    try:
        obtenu = interroger("/moi", "GET", PORT_MORT)
    finally:
        if ancien is None:
            os.environ.pop("JETON", None)
        else:
            os.environ["JETON"] = ancien
    libelle = "la sonde partagée rend bien un code, et un seul"
    if obtenu == "000":
        print(f"  [OK   ] {libelle:<62} -> {obtenu}")
    else:
        print(f"  [ÉCHEC] {libelle:<62} -> {obtenu} (attendu 000)")
        echecs += 1

    print()
    print(f"{total} cas, dont quatre qui doivent conclure autre chose que « le jeton est mort ».")
    if echecs != 0:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce script.")
        return 1
    print("Auto-test concluant.")
    return 0


def juger() -> int:
    """Le controle reel : le secret, puis la plateforme."""
    # L absence du secret garde son message a elle : « le poser » et « en poser un frais » envoient
    # au meme endroit, mais ne racontent pas la meme histoire a qui lit le journal.
    if not os.environ.get("JETON"):
        print(
            "::error::Tournage connecté demandé, secret VIGIECHIRO_TOKEN_TOURNAGE absent. Le poser :"
        )
        print(
            "::error::gh secret set VIGIECHIRO_TOKEN_TOURNAGE (jeton Vigie-Chiro, 14 jours, révoqué en fin de run)."
        )
        return 1

    # `GET /moi` est une LECTURE : elle ne consomme pas le jeton et ne touche a aucune donnee.
    code = interroger("/moi", "GET")

    import contextlib
    import io

    tampon = io.StringIO()
    with contextlib.redirect_stdout(tampon):
        etat = verdict(code)
    sortie = tampon.getvalue().rstrip("\n")
    print(sortie)
    resume = os.environ.get("GITHUB_STEP_SUMMARY")
    if resume:
        with open(resume, "a", encoding="utf-8") as f:
            f.write(sortie + "\n")

    if etat == 1:
        print(
            f"::error::La plateforme refuse le jeton du tournage (HTTP {code}) : en poser un frais "
            "avec gh secret set VIGIECHIRO_TOKEN_TOURNAGE."
        )
        return 1
    if etat != 0:
        print(
            f"::error::Plateforme injoignable (HTTP {code}) : le tournage connecté ne peut pas filmer "
            "ce qu'il prétend montrer. Ce n'est pas le jeton."
        )
        return 1
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(juger())
