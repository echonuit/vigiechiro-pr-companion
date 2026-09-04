#!/usr/bin/env python3
"""Un jeton Vigie-Chiro a une FORME, et les textes d un tournage partaient sans que rien la cherche.

(#4327, chantier #4291 ; porte du bash en #5231.)

## La forme, mesuree et non deduite

Dans le code de la plateforme, `vigiechiro/xin/auth.py:212-213` :

    new_token = ''.join(random.choice(string.ascii_uppercase + string.digits) for x in range(32))

Un jeton est donc **exactement** trente-deux caracteres pris dans `[A-Z0-9]`. `verifie_jeton.py`
ecrit qu un jeton est « une chaine opaque, sans prefixe distinctif » : c est vrai des CATALOGUES de
fournisseurs, et faux de la forme. Les deux gardes ne cherchent d ailleurs pas la meme chose - lui le
CONTEXTE dans le contenu versionne, celui-ci la FORME dans ce qui part en artefact.

## Le motif est resserre d un cran, et la raison se calcule

`[A-Z0-9]{32}` seul attraperait toute empreinte MD5 ecrite en majuscules. On exige donc **au moins
une lettre hors de `A-F`** : un jeton tire sur trente-six symboles n en manque qu avec une
probabilite de (16/36)^32, soit de l ordre de 10^-11. Toute la famille hexadecimale disparait sans
que la detection y perde quoi que ce soit.

Et la suite doit faire trente-deux caracteres EXACTEMENT, bornes comprises : une empreinte SHA-256 en
majuscules en fait soixante-quatre, et on ne veut pas y decouper un faux positif. C est pourquoi les
suites se decoupent d abord, puis se mesurent - un motif de trente-deux caracteres en trouverait
dans soixante-quatre.

## IL NE PROTEGE PAS L IMAGE, ET IL LE DIT

C est la partie qui compte le plus. Un garde qui rougirait sur `tournage.log` en laissant passer un
clip ferait croire le canal couvert alors que **le seul qui compte** - celui que le masquage de
GitHub n atteint pas, parce que le masquage ne couvre que les journaux - resterait ouvert.

Il **nomme donc ce qu il n a pas lu** : combien de fichiers ont ete ecartes parce qu ils ne sont pas
du texte, et le rappel que ceux-la ne sont couverts par personne. Un garde muet sur sa propre portee
est un faux vert avec des etapes en plus.

## Ou il tourne, et pourquoi pas plus tard

AVANT `Garder les clips en artefact`, dans le job qui filme. L idee de le mettre dans le job qui
verse parait plus sure - plus tard, plus pres de la publication - et c est l inverse : sur un depot
**public**, un artefact d Actions se telecharge SANS AUTHENTIFICATION.

## Il n imprime jamais ce qu il trouve

Le journal d un depot public est public. Rendre un jeton pour prouver qu on l a vu le publierait une
seconde fois, et plus lisiblement. Il en donne les quatre premiers caracteres et sa longueur.

Usage : python3 .github/scripts/verifie_forme_du_jeton.py [--auto-test] [répertoire...]
"""

from __future__ import annotations

import pathlib
import re
import sys

SUITE = re.compile(r"[A-Za-z0-9]+")
HORS_HEXA = re.compile(r"[G-Z]")
JETON = re.compile(r"^[A-Z0-9]+$")


def est_du_texte(fichier: pathlib.Path) -> bool:
    """Vrai si le fichier est du texte.

    Un fichier VIDE n est pas un binaire. `grep -qI .` n y trouvait aucune ligne et rendait 1, ce
    qui le rangeait parmi les ecartes : le compte de ce que le garde NE COUVRE PAS s en trouvait
    gonfle. Mesure sur un vrai artefact, ou `index.lock` pese zero octet. Il errait du bon cote,
    mais un nombre qu on ne peut pas croire ne sert a rien.
    """
    try:
        octets = fichier.read_bytes()
    except OSError:
        return False
    if not octets:
        return True
    if b"\0" in octets:
        return False
    # `grep -q .` exige une ligne portant au moins un caractere.
    return any(ligne for ligne in octets.split(b"\n"))


def suspectes(fichier: pathlib.Path) -> list[tuple[int, str]]:
    """Les suites suspectes : le numero de ligne, et les QUATRE premiers caracteres."""
    trouvees = []
    texte = fichier.read_text(encoding="utf-8", errors="replace")
    for numero, ligne in enumerate(texte.splitlines(), 1):
        for suite in SUITE.findall(ligne):
            if len(suite) == 32 and JETON.match(suite) and HORS_HEXA.search(suite):
                trouvees.append((numero, suite[:4]))
    return trouvees


def balayer(*repertoires: str | pathlib.Path) -> int:
    """Le balayage d un ou plusieurs repertoires, et le code de sortie qui va avec."""
    fichiers = sorted(
        str(p)
        for r in repertoires
        for p in pathlib.Path(r).rglob("*")
        if p.is_file() and not p.is_symlink()
    )
    lus = ecartes = trouvailles = 0
    liste_ecartes: list[str] = []
    for chemin in fichiers:
        f = pathlib.Path(chemin)
        if est_du_texte(f):
            lus += 1
            for numero, tete in suspectes(f):
                trouvailles += 1
                print(
                    f"❌ {chemin}:{numero} : suite de 32 caractères à la forme d'un jeton ({tete}…)"
                )
        else:
            ecartes += 1
            liste_ecartes.append(f"   - {chemin}")

    print()
    print(f"Textes lus : {lus}.")

    # Le passage qui empeche ce garde d etre un faux vert. Il ne dit pas « rien trouve », il dit ce
    # qu il a regarde ET ce qu il n a pas pu regarder.
    if ecartes > 0:
        print(f"Fichiers ÉCARTÉS parce qu'ils ne sont pas du texte : {ecartes}.")
        for ligne in liste_ecartes:
            print(ligne)
        print(
            "   ⚠️ Ceux-là ne sont couverts par AUCUN garde. Une image porte ce qu'elle montre, et le"
        )
        print(
            "      masquage de GitHub ne couvre que les journaux. Ce qui protège un clip est en amont :"
        )
        print(
            "      le jeton n'entre pas par l'écran, et il est révoqué en fin de tournage (#4305)."
        )
    else:
        print("Aucun fichier écarté : tout ce qui était là était du texte.")

    if trouvailles > 0:
        print()
        print("Un jeton Vigie-Chiro fait exactement 32 caractères de [A-Z0-9]. Si c'en est un :")
        print(
            "  1. le RÉVOQUER : curl -X POST -u '<le jeton>:' https://vigiechiro.herokuapp.com/api/v1/logout"
        )
        print("  2. en poser un frais : gh secret set VIGIECHIRO_TOKEN_TOURNAGE")
        return 1
    return 0


JETON_D_ESSAI = "K3QZ7M2XW9PD4BVN6TRY8CHJ5FGL0SAU"
RESSEMBLANTS = (
    "empreinte 9f2c1ab34de5678901234567890abcdef1234567890abcdef1234567890abcd",
    # EXACTEMENT 32 caracteres, et rien que de l hexadecimal : c est le seul controle qui exerce la
    # regle « au moins une lettre hors de A-F ». Il en faisait 33 a l ecriture, et la mutation qui
    # retire cette regle ne faisait alors RIEN rougir.
    "empreinte MD5 majuscule 9F2C1AB34DE5678901234567890ABCDE",
    "identifiant mongo 5f2b8c1e9a3d7f4b2e6c1a09",
    "sha-256 majuscule ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789",
    "base64 aGVsbG8gd29ybGQgdGhpcyBpcyBub3QgYSB0b2tlbg==",
    "trop court K3QZ7M2XW9PD4BVN6TRY8CHJ5FGL0SA",
    "trop long K3QZ7M2XW9PD4BVN6TRY8CHJ5FGL0SAUX",
)


def _auto_test() -> int:
    """Les sept cas de la version bash, dont trois qui doivent rester VERTS."""
    import contextlib
    import io
    import tempfile

    total = echecs = 0
    print("AUTO-TEST")

    with tempfile.TemporaryDirectory(prefix="vc-forme-") as tmp:
        racine = pathlib.Path(tmp)

        def sortie_du_balayage() -> tuple[str, int]:
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(io.StringIO()):
                code = balayer(racine)
            return tampon.getvalue(), code

        def essai(attendu: str, nom: str) -> None:
            nonlocal total, echecs
            _, code = sortie_du_balayage()
            obtenu = "vert" if code == 0 else "rouge"
            total += 1
            if obtenu == attendu:
                print(f"  [OK   ] {nom:<64} -> {obtenu}")
            else:
                print(f"  [ÉCHEC] {nom:<64} -> {obtenu} (attendu {attendu})")
                echecs += 1

        def dit(libelle: str, attendu: str, si_oui: str, si_non: str) -> None:
            nonlocal total, echecs
            sortie, _ = sortie_du_balayage()
            total += 1
            if attendu in sortie:
                print(f"  [OK   ] {libelle:<64} -> {si_oui}")
            else:
                print(f"  [ÉCHEC] {libelle:<64} -> {si_non}")
                echecs += 1

        def tait(libelle: str, interdit: str, si_absent: str, si_present: str) -> None:
            nonlocal total, echecs
            sortie, _ = sortie_du_balayage()
            total += 1
            if interdit in sortie:
                print(f"  [ÉCHEC] {libelle:<64} -> {si_present}")
                echecs += 1
            else:
                print(f"  [OK   ] {libelle:<64} -> {si_absent}")

        # Un jeton de forme exacte, tel que la plateforme les frappe.
        (racine / "tournage.log").write_text(
            f"session ouverte, jeton={JETON_D_ESSAI}\n", encoding="utf-8"
        )
        essai("rouge", "un jeton de forme exacte dans un journal")

        # Les controles negatifs : tout ce qui ressemble sans en etre.
        (racine / "tournage.log").unlink()
        (racine / "index.md").write_text("".join(l + "\n" for l in RESSEMBLANTS), encoding="utf-8")
        essai("vert", "empreintes, identifiants, base64 et longueurs voisines")

        # Un fichier vide se compte comme un texte lu, pas comme un binaire ecarte.
        (racine / "index.lock").touch()
        dit(
            "un fichier vide compte parmi les textes, pas parmi les écartés",
            "Aucun fichier écarté",
            "compté",
            "ÉCARTÉ À TORT",
        )
        (racine / "index.lock").unlink()

        # LE controle de ce garde. Une image qui contient le motif dans ses octets doit le laisser
        # VERT : il ne sait pas lire une image, et pretendre le contraire serait pire que rien.
        (racine / "clip.png").write_bytes(
            b"\x89PNG\r\n\x1a\n\x00\x00" + JETON_D_ESSAI.encode() + b"\x00\x00"
        )
        essai("vert", "une image portant le motif ne rougit pas : il ne sait pas la lire")

        # ... et il doit DIRE ce qu il a ecarte.
        dit(
            "et il nomme le fichier qu il n a pas lu",
            "Fichiers ÉCARTÉS parce qu'ils ne sont pas du texte : 1",
            "dit",
            "MUET",
        )

        # Le message doit nommer le fichier et la ligne : c est lui qu on lira, pas ce script.
        (racine / "index.md").unlink()
        (racine / "clip.png").unlink()
        (racine / "tournage.log").write_text(
            f"rien\nrien\njeton={JETON_D_ESSAI}\n", encoding="utf-8"
        )
        dit("le message nomme le fichier ET la ligne", "tournage.log:3 :", "nommé", "vague")

        # Et il ne rend JAMAIS le jeton entier : le journal d un depot public est public.
        tait("il ne réimprime pas le jeton qu il a trouvé", JETON_D_ESSAI, "masqué", "PUBLIÉ")

    print()
    print(
        f"{total} cas, dont trois qui doivent rester VERTS et un qui vérifie que le garde avoue sa portée."
    )
    if echecs != 0:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce script.")
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    if len(sys.argv) == 1:
        print(f"Usage : {sys.argv[0]} [--auto-test] <répertoire...>")
        sys.exit(2)
    etat = balayer(*sys.argv[1:])
    if etat != 0:
        print(
            "::error::Forme de jeton Vigie-Chiro dans un fichier qui allait partir en artefact. "
            "Rien n est versé."
        )
    sys.exit(etat)
