"""Les sorties d un script de la CI s ecrivent en UTF-8, quelle que soit la console (#5197).

Python ecrit dans l encodage de la console, et sous Windows c est cp1252 : un « ✓ » ou un « ⚠️ »
n y existe pas, et le script meurt sur `UnicodeEncodeError` en plein compte rendu. Le job rougit
alors sur l ecriture, et son rouge accuse ce que le script mesurait. C est arrive au tournage
(#5195), puis au tri des echecs de plateforme, treize tests verts suivis d un rouge (#6200).

Le remede vit ici et non dans l atelier, par une variable posee sur l etape : un script s appelle de
plusieurs endroits, et un remede porte par l appelant s oublie au prochain appel. Chaque script
qu un job capable de tourner sous Windows lance appelle donc `ecrire_en_utf8()` a son chargement, et
`verifie_sorties_sous_windows.py` refuse celui qui ne le fait pas.
"""

from __future__ import annotations

import sys


def ecrire_en_utf8() -> None:
    """Passe la sortie et l erreur standard en UTF-8.

    Un flux remplace par un tampon, comme le font les auto-tests, n a pas `reconfigure` : on le
    laisse tel quel, puisqu il accepte deja tout caractere.
    """
    for flux in (sys.stdout, sys.stderr):
        if hasattr(flux, "reconfigure"):
            flux.reconfigure(encoding="utf-8")
