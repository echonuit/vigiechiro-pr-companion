#!/usr/bin/env python3
"""La fraicheur des outillages npm figes par lockfile, par leur version ET par l age du retard (#6095).

Deux dossiers du depot figent un outillage par lockfile : `.github/openspec/` (la ligne de commande
OpenSpec) et `.github/release/` (semantic-release et ses greffons). Figer est la bonne decision, et
elle a un prix que l EPIC #5290 nomme dans son titre : un epinglage vieillit sans rien faire rougir.

## Pourquoi ce garde existe alors que Dependabot a une entree

Parce que cette entree ne produit rien. Mesure du 2026-10-06 : 40 executions `npm` en echec sur 40
depuis le 4 aout, sept soumissions refusees sur sept. Dependabot trouve la version, soumet la
demande, et son propre service la refuse a `create_pull_request` (`dependency_file_not_supported`).
Le defaut est connu en amont (dependabot/dependabot-core #15721 et #15237) et n est pas corrige.
Le releve complet est dans le corps de #5290.

Le jour du releve, OpenSpec etait epingle a 1.12.0 quand le registre rendait 1.14.1, et deux greffons
de l outillage de publication avaient une MAJEURE de retard depuis juillet. Personne ne le savait par
le depot.

## Sa decision est celle de son patron

`verifie_fraicheur_actions.py` (#3382) fait la meme chose pour les actions epinglees par SHA, et ce
garde reprend sa regle mot pour mot plutot que d en inventer une seconde :

- **la version** : une majeure de retard bloque, un retard dans la meme majeure avertit sans bloquer
  - sinon le garde rougirait a chaque publication amont, et on apprendrait a ne plus le lire ;
- **l age du retard** : le nombre de jours ecoules depuis la publication de la PREMIERE version
  stable que l epinglage n a pas. Au-dessus du seuil, un retard mineur bloque aussi. Les seuils sont
  ceux du patron, 180 et 365 jours.

L age n est pas celui de la version epinglee. Un paquet que son auteur n a pas republie depuis trois
ans est epingle a une version de trois ans ET a jour : il n y a rien a monter. Ce qui vieillit est le
RETARD, et il ne commence que le jour ou une version plus recente existe.

## Une preversion n est pas un retard

Le registre publie des `-beta` et des `-alpha` a cote des versions stables. Le garde lit l etiquette
`latest` du registre, qui est ce que `npm install` rendrait, et ne compte dans l age que les versions
sans suffixe.

## Ce qu il ne peut pas lire, il le refuse

Un registre qui ne repond pas, ou qui rend une reponse sans `latest`, n est pas « a jour ». Le garde
sort alors en 2 en nommant chaque paquet qu il n a pas pu lire, apres avoir dit ce qu il a lu des
autres. Un `1` dit « j ai juge et c est rouge », un `2` dit « je n ai pas pu juger » (ADR 5407).

## Il interroge le reseau, donc il n a pas de CONTRAT

Un garde qui declare son contrat entre dans la porte locale, et celle-ci ne doit dependre d aucun
service. Celui-ci tourne chaque lundi dans `securite-dependances.yml`, a cote de son patron. Son
auto-test, lui, ne touche pas le reseau : le registre s y injecte.

## Sur une demande, le retard est un CONSTAT et non un verdict (`--constat`)

Le job ne juge pour de vrai que le lundi. Mais une etape que seul un `schedule` exerce peut etre
fusionnee cassee, donc la demande qui touche ce garde ou son atelier le lance aussi. Il lirait alors
le retard REEL du depot, et le rouge tomberait sur une demande qui n y est pour rien : c est celle
qui pose ce garde qui l aurait recu la premiere, les deux majeures de l outillage de publication
etant deja la.

`--constat` separe donc les deux questions. Le garde mesure, ecrit tout ce qu il ecrit d ordinaire,
et sort en 0 sur un retard. Il sort toujours en 2 quand il n a pas pu lire : c est precisement ce
que la demande doit prouver, que l etape fonctionne.

Usage : python3 .github/scripts/verifie_fraicheur_npm.py [--racine <dossier>] [--constat]
        python3 .github/scripts/verifie_fraicheur_npm.py --inventaire
        python3 .github/scripts/verifie_fraicheur_npm.py --auto-test
"""

from __future__ import annotations

import datetime
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import MARQUE_CAUSE, cas_d_auto_test, refuse

# Les manifestes figes par lockfile. Une liste NOMMEE et non un balayage : un `package.json` neuf
# ailleurs dans le depot serait un outillage de plus a decider, pas une ligne a suivre en silence.
MANIFESTES = (
    ".github/openspec/package.json",
    ".github/release/package.json",
)

REGISTRE = "https://registry.npmjs.org"

# Les seuils du patron, repris tels quels : `verifie_fraicheur_actions.py` les a calibres sur une
# mesure du 2026-08-11. Deux gardes de fraicheur aux seuils differents demanderaient une raison.
AGE_AVERTISSEMENT = 180
AGE_ROUGE = 365

EXACTE = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")
INCONNU = "?"

GESTE_REGISTRE = (
    "Relancez le job : le registre npm bafouille parfois. S il persiste, lisez la reponse de "
    "https://registry.npmjs.org/<paquet> a la main, et regardez si le paquet a ete retire ou renomme."
)


def inventorier(racine: pathlib.Path | None = None) -> list[tuple[str, str, str]]:
    """Les epinglages `(manifeste, paquet, version demandee)` des manifestes figes, tries.

    Un manifeste absent ou illisible rend une ligne dont le paquet est `?` : il est JUGE rouge au
    lieu d etre saute, sans quoi un dossier renomme ferait passer le garde au vert en ne lisant plus
    que l autre.
    """
    base = racine or RACINE
    lignes = []
    for relatif in MANIFESTES:
        chemin = base / relatif
        try:
            manifeste = json.loads(chemin.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError, UnicodeDecodeError):
            lignes.append((relatif, INCONNU, INCONNU))
            continue
        for champ in ("dependencies", "devDependencies"):
            for paquet, demande in sorted((manifeste.get(champ) or {}).items()):
                lignes.append((relatif, paquet, str(demande)))
    return sorted(lignes)


def interroge_le_registre(paquet: str) -> dict:
    """La fiche complete d un paquet, ou un dictionnaire VIDE si le registre n a rien rendu de lisible.

    Trois tentatives, comme le patron et pour sa raison : un appel qui echoue une fois sur un service
    public n est pas une nouvelle, et un garde qui rougit au hasard s apprend a ignorer.

    La fiche COMPLETE et non l abregee (`application/vnd.npm.install-v1+json`) : seule la complete
    porte `time`, les dates de publication, dont l age du retard se derive.
    """
    adresse = f"{REGISTRE}/{urllib.parse.quote(paquet, safe='@')}"
    for essai in range(3):
        try:
            with urllib.request.urlopen(adresse, timeout=30) as reponse:
                fiche = json.loads(reponse.read().decode("utf-8"))
            if isinstance(fiche, dict):
                return fiche
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, UnicodeDecodeError):
            pass
        if essai < 2:
            time.sleep(3)
    return {}


def version_stable(texte: str) -> tuple[int, int, int] | None:
    """`(majeure, mineure, correctif)` d une version EXACTE et stable, ou `None`.

    Un intervalle (`^1.2.0`), une preversion (`1.2.0-beta.1`) et tout ce qui n est pas trois nombres
    rendent `None`. C est voulu des deux cotes : un epinglage par intervalle n est pas un epinglage,
    et une preversion du registre n est pas un retard.
    """
    trouve = EXACTE.match(texte or "")
    return (int(trouve[1]), int(trouve[2]), int(trouve[3])) if trouve else None


def age_du_retard(epingle: str, fiche: dict, aujourd_hui: datetime.date) -> str:
    """Les jours ecoules depuis la premiere version stable que l epinglage n a pas, en chaine.

    Rend `0` quand aucune version stable ne depasse l epinglage, et `?` quand une version le depasse
    mais que sa date est illisible : une date manquante n est pas un retard nul.
    """
    reference = version_stable(epingle)
    if reference is None:
        return INCONNU
    dates = []
    for version, quand in (fiche.get("time") or {}).items():
        candidate = version_stable(version)
        if candidate is None or candidate <= reference:
            continue
        try:
            dates.append(datetime.date.fromisoformat(str(quand)[:10]))
        except ValueError:
            return INCONNU
    if not dates:
        return "0"
    return str(max((aujourd_hui - min(dates)).days, 0))


def mesurer(
    epinglages: list[tuple[str, str, str]],
    registre,
    aujourd_hui: datetime.date,
) -> list[tuple[str, str, str, str, str]]:
    """Une ligne `(manifeste, paquet, epingle, amont, retard)` par epinglage.

    `registre` est la fonction qui rend la fiche d un paquet : celle du reseau en service,
    une fausse dans l auto-test. `amont` vaut `?` quand le registre n a rien rendu de lisible.
    """
    lignes = []
    for manifeste, paquet, epingle in epinglages:
        if paquet == INCONNU:
            lignes.append((manifeste, paquet, epingle, INCONNU, INCONNU))
            continue
        fiche = registre(paquet) or {}
        amont = str((fiche.get("dist-tags") or {}).get("latest") or INCONNU)
        retard = INCONNU if amont == INCONNU else age_du_retard(epingle, fiche, aujourd_hui)
        lignes.append((manifeste, paquet, epingle, amont, retard))
    return lignes


def juger(lignes: list[tuple[str, str, str, str, str]]) -> int:
    """Le verdict sur les lignes mesurees : 0 a jour ou averti, 1 rouge, et 2 par refus."""
    if not lignes:
        print("ROUGE : inventaire vide, aucune dépendance examinée.")
        print("   Ce n'est pas « tout est à jour », c'est « la question n'a pas été posée ».")
        print("   Regardez les manifestes nommés dans MANIFESTES et ce que rend --inventaire.")
        return 1

    rouges: list[str] = []
    avertis: list[str] = []
    muets: list[str] = []
    a_jour = 0

    for manifeste, paquet, epingle, amont, retard in lignes:
        ou = f"{paquet} ({manifeste})"
        if paquet == INCONNU:
            rouges.append(f"   {manifeste} : manifeste absent ou illisible")
            continue
        voulue = version_stable(epingle)
        if voulue is None:
            rouges.append(
                f"   {ou} : épinglé par « {epingle} », qui n'est pas une version exacte : "
                "le prochain « npm install » déplacerait le lockfile sans qu'aucun diff du "
                "manifeste ne le montre"
            )
            continue
        rendue = version_stable(amont)
        if rendue is None:
            muets.append(f"{paquet} (réponse du registre : « {amont} »)")
            continue
        if rendue <= voulue:
            a_jour += 1
            continue

        ecart = f"{epingle} -> {amont}"
        if not retard.isdigit():
            rouges.append(f"   {ou} : {ecart}, âge du retard indéterminé")
            continue
        age = int(retard)
        depuis = f"en retard depuis {age} jours"
        if rendue[0] != voulue[0]:
            rouges.append(f"   {ou} : {ecart} (une MAJEURE de retard), {depuis}")
        elif age >= AGE_ROUGE:
            rouges.append(f"   {ou} : {ecart}, {depuis} : au-delà de {AGE_ROUGE} jours")
        elif age >= AGE_AVERTISSEMENT:
            avertis.append(f"   {ou} : {ecart}, {depuis} : au-delà de {AGE_AVERTISSEMENT} jours")
        else:
            avertis.append(f"   {ou} : {ecart}, {depuis}")

    if rouges:
        print(f"ROUGE : {len(rouges)} dépendance(s) à regarder :")
        print("\n".join(rouges))
    if avertis:
        print(
            f"SIGNALÉ : {len(avertis)} dépendance(s) en retard dans la même majeure "
            "(non bloquant) :"
        )
        print("\n".join(avertis))
    print(
        f"Fraîcheur des outillages npm : {len(lignes)} dépendance(s) examinée(s), {a_jour} à jour, "
        f"{len(avertis)} en retard mineur, {len(rouges)} bloquante(s), {len(muets)} non lue(s)."
    )
    if muets:
        # APRES le compte rendu, et non a sa place : ce qui a ete lu reste vrai et se dit. Mais le
        # garde ne conclut pas, parce qu une dependance non lue n est ni a jour ni en retard.
        refuse(
            f"le registre npm n'a rien rendu de lisible pour {len(muets)} paquet(s) : "
            + ", ".join(muets)
            + ". Ce garde ne conclut pas sur ce qu'il n'a pas lu.",
            GESTE_REGISTRE,
        )
    return 1 if rouges else 0


def code_de_sortie(code: int, constat: bool) -> int:
    """Le code rendu a l appelant : en mode constat, un retard (1) ne fait pas rougir, un refus si.

    Le refus ne passe pas par ici, il sort par exception avant. Cette fonction ne recoit donc que
    0 ou 1, et ne transforme que le 1.
    """
    if constat and code == 1:
        print(
            "CONSTAT : ce retard est celui du dépôt, pas le verdict de cette demande. "
            "Le job du lundi, lui, rougit dessus."
        )
        return 0
    return code


def _fausse_fiche(latest: str, publications: dict[str, str]) -> dict:
    """Une fiche de registre fabriquee : l etiquette `latest` et les dates de publication."""
    return {"dist-tags": {"latest": latest}, "time": dict(publications)}


def _joue_pour_auto_test(epinglages, fiches: dict[str, dict], jour: str) -> tuple[object, str]:
    """Mesure puis juge sur un faux registre, en capturant ce que le garde ecrit. Rend (code, sortie).

    Le `SystemExit` est rattrape : le refus sort par une exception, et un auto-test qui la laisserait
    passer s arreterait au premier cas de registre muet.
    """
    import contextlib
    import io

    tampon = io.StringIO()
    with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
        try:
            code = juger(mesurer(epinglages, fiches.get, datetime.date.fromisoformat(jour)))
        except SystemExit as fin:
            code = fin.code
    return code, tampon.getvalue()


def _sans_bruit_pour_auto_test(calcul):
    """Rend la valeur de `calcul` en avalant ce qu il ecrit : un cas ne doit pas salir la sortie."""
    import contextlib
    import io

    with contextlib.redirect_stdout(io.StringIO()):
        return calcul()


def _auto_test() -> int:
    """Chaque cas porte son code ET le fragment exige dans la sortie : un `1` peut venir d ailleurs.

    Les expressions sont passees en `lambda` : sous mutation, une fonction neutralisee fait lever
    l expression, et le cas doit le NOMMER au lieu d arreter le temoin sur une trace (#5444).
    """
    import tempfile

    verifie, echecs = cas_d_auto_test()
    jour = "2026-10-06"
    m = ".github/x/package.json"

    def cas(libelle: str, epinglages, fiches, code_attendu: int, fragment: str) -> None:
        verifie(
            f"{libelle} : code",
            lambda: _joue_pour_auto_test(epinglages, fiches, jour)[0],
            code_attendu,
        )
        verifie(
            f"{libelle} : le message dit « {fragment} »",
            lambda: fragment in _joue_pour_auto_test(epinglages, fiches, jour)[1],
            True,
        )

    cas(
        "tout à jour",
        [(m, "a", "1.2.3")],
        {"a": _fausse_fiche("1.2.3", {"1.2.3": "2024-01-01T00:00:00.000Z"})},
        0,
        "1 dépendance(s) examinée(s), 1 à jour",
    )
    # Le cas reel du 2026-10-06, rejoue : `@semantic-release/changelog` 6.0.3 contre 7.0.0.
    cas(
        "une majeure de retard",
        [(m, "a", "6.0.3")],
        {
            "a": _fausse_fiche(
                "7.0.0", {"6.0.3": "2023-03-23T00:00:00Z", "7.0.0": "2026-07-21T00:00:00Z"}
            )
        },
        1,
        "une MAJEURE de retard",
    )
    # L autre cas reel du meme jour : OpenSpec 1.12.0 contre 1.14.1. L age part de 1.13.0, la
    # PREMIERE version manquee, et non de la derniere : 2026-09-10 -> 2026-10-06, vingt-six jours.
    cas(
        "retard mineur récent, signalé sans bloquer",
        [(m, "a", "1.12.0")],
        {
            "a": _fausse_fiche(
                "1.14.1",
                {
                    "1.12.0": "2026-09-01T00:00:00Z",
                    "1.13.0": "2026-09-10T00:00:00Z",
                    "1.14.1": "2026-10-05T00:00:00Z",
                },
            )
        },
        0,
        "1.12.0 -> 1.14.1, en retard depuis 26 jours",
    )
    cas(
        "retard mineur au-delà du seuil d'avertissement, non bloquant",
        [(m, "a", "1.0.0")],
        {"a": _fausse_fiche("1.1.0", {"1.1.0": "2026-03-01T00:00:00Z"})},
        0,
        "au-delà de 180 jours",
    )
    cas(
        "retard mineur au-delà du seuil rouge, bloquant",
        [(m, "a", "1.0.0")],
        {"a": _fausse_fiche("1.1.0", {"1.1.0": "2025-03-01T00:00:00Z"})},
        1,
        "au-delà de 365 jours",
    )
    # Controles NEGATIFS : la regle doit rester etroite.
    cas(
        "une préversion plus récente n'est pas un retard",
        [(m, "a", "25.0.9")],
        {
            "a": _fausse_fiche(
                "25.0.9",
                {"25.0.9": "2026-08-05T00:00:00Z", "26.0.0-beta.1": "2026-08-07T00:00:00Z"},
            )
        },
        0,
        "1 à jour",
    )
    # La meme regle vue dans l AGE : une preversion publiee AVANT la premiere version stable manquee
    # ne fait pas partir le retard plus tot. Comptee, elle rendrait soixante jours au lieu de cinq.
    cas(
        "une préversion n'avance pas le début du retard",
        [(m, "a", "25.0.8")],
        {
            "a": _fausse_fiche(
                "25.0.9",
                {"26.0.0-beta.1": "2026-08-07T00:00:00Z", "25.0.9": "2026-10-01T00:00:00Z"},
            )
        },
        0,
        "25.0.8 -> 25.0.9, en retard depuis 5 jours",
    )
    cas(
        "un paquet ancien et sans successeur est à jour",
        [(m, "a", "6.0.3")],
        {"a": _fausse_fiche("6.0.3", {"6.0.3": "2020-01-01T00:00:00Z"})},
        0,
        "1 à jour",
    )
    # Une mesure ratee n est pas un « a jour ».
    cas(
        "registre muet : le garde refuse, il ne rend pas « à jour »",
        [(m, "a", "1.0.0")],
        {},
        2,
        MARQUE_CAUSE,
    )
    cas(
        "registre muet pour un seul paquet : l'autre est dit, et le garde refuse quand même",
        [(m, "a", "1.0.0"), (m, "b", "1.0.0")],
        {"a": _fausse_fiche("1.0.0", {"1.0.0": "2024-01-01T00:00:00Z"})},
        2,
        "1 à jour, 0 en retard mineur, 0 bloquante(s), 1 non lue(s)",
    )
    cas(
        "fiche sans étiquette latest : refus",
        [(m, "a", "1.0.0")],
        {"a": {"time": {"1.0.0": "2024-01-01T00:00:00Z"}}},
        2,
        "rien rendu de lisible pour 1 paquet(s)",
    )
    cas(
        "date de publication illisible : rouge, pas un retard nul",
        [(m, "a", "1.0.0")],
        {"a": _fausse_fiche("1.1.0", {"1.1.0": "hier"})},
        1,
        "âge du retard indéterminé",
    )
    cas(
        "épinglage par intervalle : rouge",
        [(m, "a", "^1.0.0")],
        {"a": _fausse_fiche("1.0.0", {"1.0.0": "2024-01-01T00:00:00Z"})},
        1,
        "n'est pas une version exacte",
    )
    cas("inventaire vide : rouge", [], {}, 1, "inventaire vide")
    cas(
        "manifeste illisible : rouge, pas sauté",
        [(m, INCONNU, INCONNU)],
        {},
        1,
        "manifeste absent ou illisible",
    )

    # L INVENTAIRE, sur des manifestes fabriques : il lit les deux champs, et un manifeste absent
    # rend une ligne au lieu de disparaitre.
    with tempfile.TemporaryDirectory(prefix="vc-npm-") as tmp:
        bac = pathlib.Path(tmp)
        (bac / ".github" / "openspec").mkdir(parents=True)
        (bac / ".github" / "openspec" / "package.json").write_text(
            json.dumps({"dependencies": {"b": "2.0.0"}, "devDependencies": {"a": "1.0.0"}}),
            encoding="utf-8",
        )
        attendu = [
            (MANIFESTES[0], "a", "1.0.0"),
            (MANIFESTES[0], "b", "2.0.0"),
            (MANIFESTES[1], INCONNU, INCONNU),
        ]
        verifie(
            "l'inventaire lit les deux champs, et rend une ligne pour le manifeste absent",
            lambda: inventorier(bac),
            attendu,
        )

    # Le mode constat : il ne desarme que le retard, et il le DIT.
    verifie(
        "constat : un retard ne fait pas rougir",
        lambda: _sans_bruit_pour_auto_test(lambda: code_de_sortie(1, True)),
        0,
    )
    verifie("hors constat : un retard fait rougir", lambda: code_de_sortie(1, False), 1)
    verifie("constat : un vert reste un vert", lambda: code_de_sortie(0, True), 0)

    if echecs() == 0:
        print(f"Auto-test de la fraîcheur npm : OK ({echecs.joues()} cas).")
        return 0
    print("AUTO-TEST EN ÉCHEC : ne pas se fier au verdict de ce garde.")
    return 1


def main(arguments: list[str]) -> int:
    racine = RACINE
    mode = "juger"
    constat = False
    args = list(arguments)
    while args:
        drapeau = args.pop(0)
        if drapeau == "--auto-test":
            return _auto_test()
        if drapeau == "--inventaire":
            mode = "inventaire"
        elif drapeau == "--constat":
            constat = True
        elif drapeau == "--racine" and args:
            racine = pathlib.Path(args.pop(0))
        else:
            print(f"option inconnue : {drapeau}", file=sys.stderr)
            return 2
    epinglages = inventorier(racine)
    if mode == "inventaire":
        for manifeste, paquet, epingle in epinglages:
            print(f"{manifeste}\t{paquet}\t{epingle}")
        return 0
    code = juger(
        mesurer(epinglages, interroge_le_registre, datetime.datetime.now(tz=datetime.UTC).date())
    )
    return code_de_sortie(code, constat)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
