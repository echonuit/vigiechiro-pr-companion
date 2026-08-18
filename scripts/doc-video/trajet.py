#!/usr/bin/env python3
"""Fabrique les arguments xdotool d'un deplacement de souris CONTINU.

Recupere de l'outillage qui a tourne la video de la soumission Flathub (#2191).
Ce fichier-la avait disparu avec son bac a sable de session ; il est verse ici
tel quel, seul des quatre a n'avoir rien a corriger - aucun chemin en dur, tout
passe par les arguments (#3887).

xdotool mousemove teleporte le curseur : a l'image, il saute. On interpole donc
le trajet, avec une courbe d'acceleration (lent au depart, rapide au milieu,
lent a l'arrivee) pour imiter un geste humain, et un leger arc lateral pour
eviter la ligne droite parfaite que personne ne trace.

Tout est rendu en UNE seule ligne d'arguments, donc un seul processus xdotool :
c'est ce qui rend le mouvement fluide plutot que sacade.

    xdotool $(trajet.py x0 y0 x1 y1 duree)
"""
import sys


def main():
    x0, y0, x1, y1 = (int(v) for v in sys.argv[1:5])
    duree = float(sys.argv[5]) if len(sys.argv) > 5 else 0.55

    dx, dy = x1 - x0, y1 - y0
    distance = (dx * dx + dy * dy) ** 0.5
    if distance < 2:
        print(f"mousemove {x1} {y1}")
        return

    # 60 points par seconde : au-dela, xdotool coute plus cher que le gain visuel.
    n = max(6, int(duree * 60))
    pas = duree / n

    # Arc perpendiculaire, proportionnel a la distance et plafonne : un geste
    # humain ne suit pas la corde exacte, mais ne fait pas non plus un detour.
    arc = min(distance * 0.06, 26.0)
    px, py = (-dy / distance, dx / distance)

    morceaux = []
    for i in range(1, n + 1):
        t = i / n
        # smoothstep : derivee nulle aux deux bouts, donc ni depart ni arret brusque
        e = t * t * (3 - 2 * t)
        # cloche : nulle aux extremites, maximale au milieu
        cloche = 4 * e * (1 - e)
        x = round(x0 + dx * e + px * arc * cloche)
        y = round(y0 + dy * e + py * arc * cloche)
        morceaux.append(f"mousemove {x} {y} sleep {pas:.4f}")

    print(" ".join(morceaux))


if __name__ == "__main__":
    main()
