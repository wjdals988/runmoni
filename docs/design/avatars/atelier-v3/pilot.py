"""Hand-fitted, review-only Night outfit on a 64×96 grid."""
from PIL import Image

from paint import Pen

SIZE = 64, 96
POSES = ((0,0),(0,1),(1,0),(1,1))
INK = '#171e2e'
SKIN_SHADOW = '#ac674c'
SKIN = '#efa368'
SKIN_LIGHT = '#ffd196'
HAIR = '#2d354d'
HAIR_LIGHT = '#485571'
BROWN = '#4d3438'
BROWN_LIGHT = '#7f523e'
BROWN_GLOW = '#a46c4d'
SHELL_SHADOW = '#2a3b53'
SHELL = '#3d576d'
SHELL_LIGHT = '#56768a'
SHELL_TOP = '#7496a1'
REFLECTOR = '#c9d8b7'
SOLE = '#a8c2b8'
SOLE_LIGHT = '#e6ead1'
PALE = '#f8ecda'


def layer():
    return Image.new('RGBA', SIZE)


def far_limbs(pose):
    far = layer()
    p = Pen(far)
    if pose == (0,0):
        p.poly([(38,59),(41,65),(34,72),(24,79),(19,79),(17,74),(23,72),(32,63)],INK)
        p.poly([(36,62),(38,65),(33,70),(24,77),(20,77),(19,75),(24,74),(33,65)],SHELL_SHADOW)
        p.line([(31,69),(27,72),(23,74)],SHELL_LIGHT)
        p.poly([(17,73),(21,74),(21,79),(17,81),(15,86),(12,89),(8,88),(6,83),(7,79),(10,74),(13,72)],INK)
        p.poly([(13,74),(17,75),(19,76),(18,79),(16,80),(14,85),(12,87),(9,86),(8,83),(9,78)],SHELL_LIGHT)
        p.line([(8,83),(9,86),(11,87),(13,85),(14,81)],SOLE_LIGHT)
        p.line([(13,77),(16,78),(16,80)],SOLE)
        p.poly([(42,46),(46,46),(48,51),(50,52),(51,49),(54,49),(57,51),(57,56),(52,61),(47,62),(43,60),(42,57)],INK)
        p.poly([(44,48),(46,49),(48,54),(52,53),(55,55),(51,59),(47,60),(44,58)],SHELL_SHADOW)
        p.poly([(51,52),(52,50),(55,50),(56,53),(54,56),(51,56),(50,54)],SKIN_SHADOW)
        p.poly([(52,51),(54,51),(55,53),(53,55),(51,54)],SKIN)
        p.line([(50,56),(52,57)],REFLECTOR)
    elif pose == (0,1):
        p.poly([(36,64),(43,65),(44,72),(40,80),(37,85),(33,88),(30,86),(32,79),(35,73)],INK)
        p.poly([(38,66),(42,68),(42,72),(39,80),(35,85),(33,85),(34,80),(37,73)],SHELL_SHADOW)
        p.line([(41,73),(38,82)],SHELL_LIGHT)
        p.poly([(34,85),(37,86),(39,90),(42,92),(44,92),(45,95),(30,95),(28,94),(29,90)],INK)
        p.poly([(31,91),(34,87),(37,88),(39,91),(43,93),(43,94),(30,94)],SHELL_LIGHT)
        p.line([(32,94),(42,94)],SOLE_LIGHT)
        p.poly([(43,47),(47,48),(49,53),(51,54),(53,52),(56,52),(58,55),(58,59),(55,62),(51,63),(47,62),(44,59),(44,54)],INK)
        p.poly([(45,50),(47,50),(49,55),(52,56),(55,58),(53,61),(49,61),(46,58)],SHELL_SHADOW)
        p.poly([(52,55),(54,53),(56,54),(57,56),(56,59),(52,60),(51,58)],SKIN_SHADOW)
        p.poly([(54,54),(56,55),(56,57),(54,58),(52,57)],SKIN)
        p.line([(50,58),(51,59)],REFLECTOR)
    elif pose == (1,0):
        p.poly([(33,55),(40,60),(38,66),(33,72),(27,74),(19,74),(17,70),(21,67),(27,66),(29,60)],INK)
        p.poly([(36,58),(38,61),(37,65),(32,70),(27,72),(20,72),(19,70),(23,68),(28,68),(33,63)],SHELL_SHADOW)
        p.line([(27,69),(31,68),(35,64)],SHELL_LIGHT)
        p.poly([(17,66),(22,68),(22,73),(18,76),(15,82),(11,83),(8,80),(8,77),(10,72),(14,67)],INK)
        p.poly([(15,68),(19,69),(20,72),(16,75),(14,79),(11,81),(10,79),(11,74)],SHELL_LIGHT)
        p.line([(10,76),(10,79),(12,81),(14,79),(16,76)],SOLE_LIGHT)
        p.line([(15,71),(18,72)],SOLE)
        p.poly([(43,42),(47,42),(49,46),(51,48),(53,47),(54,44),(57,43),(59,45),(60,49),(56,53),(53,55),(49,55),(46,53),(45,50)],INK)
        p.poly([(45,44),(47,44),(49,49),(52,50),(56,49),(57,51),(53,53),(49,53),(47,51)],SHELL_SHADOW)
        p.poly([(54,46),(56,44),(58,46),(58,49),(55,51),(52,50)],SKIN_SHADOW)
        p.poly([(55,46),(57,46),(57,48),(55,49),(54,48)],SKIN)
        p.line([(52,51),(54,52)],REFLECTOR)
    else:
        p.poly([(39,57),(45,61),(46,66),(42,71),(38,76),(35,83),(31,85),(28,80),(31,73),(34,68),(34,62)],INK)
        p.poly([(40,60),(43,62),(44,66),(40,71),(37,75),(34,82),(31,82),(31,79),(34,72),(36,67),(37,63)],SHELL_SHADOW)
        p.line([(41,68),(37,73),(34,79)],SHELL_LIGHT)
        p.poly([(31,81),(35,81),(38,85),(42,86),(44,89),(43,92),(30,92),(27,91),(27,87)],INK)
        p.poly([(30,87),(33,83),(36,84),(39,87),(42,88),(42,90),(29,90)],SHELL_LIGHT)
        p.line([(29,91),(41,91)],SOLE_LIGHT)
        p.poly([(45,43),(50,44),(52,49),(56,49),(57,46),(60,45),(61,47),(62,51),(60,55),(55,57),(51,56),(48,53),(47,51)],INK)
        p.poly([(48,46),(50,46),(53,51),(56,51),(60,50),(60,53),(56,55),(52,54),(50,52)],SHELL_SHADOW)
        p.poly([(56,50),(58,46),(60,46),(62,48),(61,51),(58,53),(56,52)],SKIN_SHADOW)
        p.poly([(58,47),(60,47),(61,49),(59,51),(57,50)],SKIN)
        p.line([(54,52),(56,54)],REFLECTOR)
    return far


def near_leg(pose):
    art=layer()
    p=Pen(art)
    if pose==(0,0):
        p.poly([(28,60),(36,61),(41,66),(43,73),(48,81),(47,84),(42,86),(38,82),(35,76),(31,73),(29,69),(25,66)],INK)
        p.poly([(29,62),(35,63),(39,66),(42,73),(46,81),(45,83),(42,83),(39,79),(36,73),(33,73),(31,70),(27,66)],SHELL)
        p.poly([(28,63),(34,63),(38,66),(39,70),(35,70),(32,67),(29,68),(27,66)],SHELL_LIGHT)
        p.line([(39,72),(41,76),(43,80)],SHELL_TOP)
        p.poly([(45,80),(49,81),(52,83),(56,82),(61,83),(63,86),(62,89),(59,92),(52,95),(40,95),(39,92),(40,87)],INK)
        p.poly([(45,82),(49,83),(52,85),(57,84),(61,85),(61,88),(58,90),(51,93),(41,93),(41,90)],SHELL_LIGHT)
        p.poly([(43,87),(47,84),(50,86),(54,86),(57,85),(60,86),(59,88),(53,91),(46,92),(42,92)],SOLE)
        p.line([(42,93),(48,93),(53,92),(59,89)],SOLE_LIGHT)
        p.line([(46,85),(49,86),(51,87)],REFLECTOR)
    elif pose==(0,1):
        p.poly([(28,61),(39,62),(42,65),(41,68),(45,73),(45,78),(42,81),(37,83),(32,84),(29,81),(29,77),(34,74),(33,72),(29,72),(26,68)],INK)
        p.poly([(29,63),(38,64),(40,66),(38,69),(43,74),(43,77),(41,79),(36,82),(32,82),(31,80),(32,77),(37,74),(35,71),(30,70),(28,67)],SHELL)
        p.poly([(29,64),(34,65),(37,66),(36,69),(31,69),(28,67)],SHELL_LIGHT)
        p.line([(34,79),(38,77),(41,75)],SHELL_TOP)
        p.poly([(31,79),(33,82),(31,86),(32,90),(31,94),(28,95),(23,94),(21,90),(20,86),(20,82),(23,79),(27,78)],INK)
        p.poly([(25,80),(29,80),(31,82),(29,86),(30,90),(29,93),(26,93),(23,90),(22,86),(22,82)],SHELL_LIGHT)
        p.poly([(23,83),(25,82),(27,84),(26,87),(29,90),(28,92),(26,91),(23,88)],SOLE)
        p.line([(22,83),(22,87),(24,91),(27,93)],SOLE_LIGHT)
        p.line([(26,81),(28,82),(28,84)],REFLECTOR)
    elif pose==(1,0):
        p.poly([(27,54),(35,55),(41,59),(44,65),(52,77),(50,82),(45,83),(40,78),(37,73),(32,69),(28,67),(25,62)],INK)
        p.poly([(28,56),(34,57),(39,60),(42,65),(50,77),(49,80),(45,81),(42,77),(39,72),(34,67),(30,66),(27,62)],SHELL)
        p.poly([(29,57),(34,58),(38,61),(39,64),(36,65),(32,64),(28,61)],SHELL_LIGHT)
        p.line([(37,65),(42,70),(45,76)],SHELL_TOP)
        p.poly([(47,79),(51,78),(54,80),(58,79),(61,80),(62,84),(60,87),(55,90),(49,91),(43,91),(41,89),(42,85)],INK)
        p.poly([(47,81),(51,80),(54,82),(58,81),(60,82),(60,85),(54,88),(48,89),(43,89),(43,86)],SHELL_LIGHT)
        p.poly([(45,87),(48,84),(52,84),(56,84),(59,83),(58,86),(54,88),(48,89),(44,89)],SOLE)
        p.line([(44,89),(49,89),(55,88),(59,86)],SOLE_LIGHT)
        p.line([(47,83),(50,82),(52,83)],REFLECTOR)
    else:
        p.poly([(28,56),(40,57),(45,60),(47,64),(49,67),(48,71),(44,75),(38,76),(32,74),(30,71),(31,67),(28,65),(26,61)],INK)
        p.poly([(29,58),(39,59),(43,61),(45,65),(47,67),(46,71),(43,73),(37,74),(33,72),(32,70),(34,67),(30,64),(28,61)],SHELL)
        p.poly([(29,59),(35,60),(39,62),(39,64),(36,65),(31,63),(29,61)],SHELL_LIGHT)
        p.line([(34,70),(38,70),(43,71)],SHELL_TOP)
        p.poly([(30,69),(34,70),(36,74),(35,78),(35,82),(33,85),(29,85),(26,82),(25,78),(25,73),(27,70)],INK)
        p.poly([(28,71),(31,71),(34,73),(34,76),(33,79),(33,82),(31,83),(28,81),(27,77),(27,73)],SHELL_LIGHT)
        p.poly([(29,72),(31,73),(31,76),(30,78),(32,81),(31,82),(29,80),(28,76)],SOLE)
        p.line([(27,73),(27,77),(29,81),(31,83)],SOLE_LIGHT)
        p.line([(31,72),(33,74),(33,76)],REFLECTOR)
    return art


def jacket(pose):
    art=layer()
    p=Pen(art)
    outlines={
        (0,0):[(28,43),(35,43),(40,45),(45,45),(48,48),(49,54),(47,60),(44,65),(37,65),(31,63),(25,62),(25,57),(26,53),(24,50)],
        (0,1):[(28,44),(35,44),(41,46),(46,46),(48,50),(48,56),(45,61),(44,66),(36,67),(29,65),(26,64),(26,57),(25,51)],
        (1,0):[(26,41),(32,40),(36,41),(40,43),(44,42),(47,45),(47,50),(45,56),(44,59),(36,61),(30,59),(26,58),(25,52),(23,48)],
        (1,1):[(31,42),(36,41),(40,42),(43,44),(48,43),(51,46),(51,51),(49,56),(47,61),(39,62),(33,60),(29,59),(29,53),(28,48)],
    }
    p.poly(outlines[pose],INK)
    # Torso art for the first stride and the passing pose uses the fitted waist anchors.
    left,top={(0,0):(26,45),(0,1):(27,46),(1,0):(25,42),(1,1):(30,43)}[pose]
    dx,dy=left-26,top-45
    p=Pen(art,lambda x,y:(x+dx,y+dy))
    p.poly([(26,48),(30,45),(35,45),(39,47),(43,47),(46,47),(47,50),(47,55),(45,60),(43,63),(37,63),(30,61),(27,60),(27,55)],SHELL)
    p.poly([(27,48),(31,45),(35,45),(39,47),(44,47),(46,47),(47,50),(43,51),(39,51),(35,49),(30,51),(27,52)],SHELL_TOP)
    p.poly([(36,52),(39,53),(40,58),(38,62),(35,62),(33,60),(33,56)],SHELL_SHADOW)
    p.poly([(46,54),(46,57),(43,61),(40,62),(42,58)],SHELL_LIGHT)
    p.line([(35,45),(36,48),(39,50),(42,49),(43,47)],SHELL_SHADOW,2)
    p.line([(39,51),(39,56),(38,60),(38,63)],INK)
    p.line([(40,52),(40,56),(39,58)],SHELL_TOP)
    p.line([(29,52),(34,53),(37,54)],REFLECTOR,2)
    p.line([(42,54),(45,53)],REFLECTOR)
    p.line([(29,58),(33,57)],SHELL_LIGHT)
    p.line([(31,62),(35,63),(41,63)],SHELL_SHADOW)
    return art


def near_arm(pose):
    art=layer()
    p=Pen(art)
    shapes={
        (0,0): ([(27,45),(32,45),(34,47),(33,51),(29,53),(25,55),(23,58),(24,60),(22,64),(18,65),(15,63),(14,60),(16,55),(20,50),(23,47)],
                [(28,47),(32,47),(32,49),(28,52),(23,54),(21,58),(17,59),(16,57),(19,52),(23,49)],
                [(17,58),(20,57),(23,60),(21,63),(18,63),(16,61)],[(17,58),(20,57),(22,60)]),
        (0,1): ([(27,46),(32,46),(34,48),(31,52),(28,55),(28,57),(31,57),(34,58),(38,57),(40,59),(39,63),(36,65),(33,64),(29,63),(25,62),(22,59),(22,55),(24,50)],
                [(28,48),(32,48),(31,50),(28,54),(26,57),(27,59),(32,59),(32,62),(28,62),(25,60),(24,58),(24,54)],
                [(33,59),(36,58),(38,59),(38,62),(36,63),(33,62),(32,60)],[(31,58),(32,60),(32,62)]),
        (1,0): ([(25,43),(28,43),(31,45),(30,49),(25,51),(23,54),(22,56),(23,58),(20,61),(16,61),(14,59),(14,55),(16,51),(19,47)],
                [(25,45),(28,45),(29,47),(27,49),(23,51),(21,55),(17,56),(16,54),(18,50),(22,47)],
                [(17,55),(20,55),(22,57),(20,59),(17,59),(16,58)],[(16,54),(19,55),(21,56)]),
        (1,1): ([(29,43),(33,43),(36,45),(34,48),(30,51),(30,52),(33,53),(36,54),(38,53),(41,55),(41,58),(39,61),(35,61),(32,59),(29,58),(26,56),(25,53),(26,49)],
                [(30,45),(34,45),(34,47),(30,50),(28,53),(29,55),(34,56),(34,58),(31,57),(28,56),(27,53),(28,49)],
                [(35,55),(37,54),(40,55),(40,58),(38,60),(35,59),(34,57)],[(33,54),(34,56),(34,58)]),
    }
    outline, cloth, hand, cuff=shapes[pose]
    p.poly(outline,INK)
    p.poly(cloth,SHELL)
    p.line(cloth[:4],SHELL_LIGHT)
    p.poly(hand,SKIN_SHADOW)
    p.poly([(x,y-1) for x,y in hand[:-1]],SKIN)
    p.line(cuff,REFLECTOR)
    return art


def face(pose):
    art=layer()
    g,f=pose
    dx,dy=((1,1) if f else (0,0)) if not g else ((4,1) if f else (0,0))
    p=Pen(art,lambda x,y:(x+dx,y+dy))
    if not g:
        p.poly([(18,23),(20,18),(27,13),(35,10),(43,11),(47,15),(51,14),(53,19),(56,21),(57,26),(54,30),(55,33),(53,36),(52,40),(50,44),(44,46),(37,46),(34,44),(30,43),(26,44),(22,42),(20,38),(19,33),(17,29)],INK)
        p.poly([(20,25),(24,20),(31,16),(39,14),(46,14),(48,17),(51,17),(52,20),(55,22),(54,27),(50,29),(47,27),(43,28),(40,29),(36,30),(34,34),(31,39),(28,41),(25,41),(21,38),(20,34),(19,29)],HAIR)
        p.poly([(23,24),(29,20),(34,18),(40,17),(42,18),(36,21),(30,23),(26,26),(22,28)],HAIR_LIGHT)
        p.poly([(35,32),(40,30),(44,28),(48,28),(51,30),(53,33),(52,38),(50,42),(46,44),(39,44),(34,42),(32,39),(32,35)],SKIN_SHADOW)
        p.poly([(35,34),(39,31),(43,31),(46,29),(49,30),(52,33),(51,38),(49,42),(45,43),(38,43),(34,40),(33,36)],SKIN)
        p.poly([(25,35),(28,34),(30,35),(31,38),(30,41),(27,42),(24,40),(23,38)],SKIN_SHADOW)
        p.poly([(25,35),(27,35),(29,36),(29,39),(28,40),(25,39),(24,37)],SKIN)
        p.line([(25,36),(26,36),(26,38)],SKIN_LIGHT)
        p.line([(35,33),(38,33),(40,33)],INK)
        p.line([(47,32),(50,32)],INK)
        p.poly([(35,35),(39,34),(41,35),(41,38),(39,40),(36,39),(35,37)],PALE)
        p.poly([(37,35),(39,34),(40,36),(39,38),(37,38)],INK)
        p.poly([(47,34),(50,33),(51,34),(51,37),(50,38),(47,37)],PALE)
        p.line([(50,34),(50,36),(49,37)],INK)
        p.line([(44,37),(45,38),(44,39)],SKIN_LIGHT)
        p.line([(42,40),(44,41),(46,40)],INK)
        p.poly([(33,43),(36,44),(42,44),(42,47),(39,49),(36,48),(34,46)],SKIN_SHADOW)
        p.line([(36,45),(37,46),(39,47)],SKIN)
    else:
        p.poly([(8,8),(12,4),(17,3),(22,4),(26,6),(29,10),(34,10),(40,9),(45,10),(50,13),(53,17),(55,21),(57,22),(57,26),(55,29),(54,34),(53,39),(50,42),(47,44),(41,44),(37,43),(34,42),(30,40),(27,39),(25,35),(25,30),(23,26),(21,29),(18,30),(16,32),(12,33),(8,31),(4,28),(3,24),(3,20),(5,18),(7,16)],INK)
        p.poly([(9,9),(13,6),(17,5),(22,6),(26,9),(27,13),(24,18),(23,22),(20,26),(17,27),(14,30),(11,30),(7,28),(5,25),(6,22),(4,21),(9,18),(9,15)],BROWN)
        p.poly([(13,9),(16,7),(20,7),(23,9),(21,11),(18,13),(16,16),(13,19),(10,21),(8,21),(11,17),(11,13)],BROWN_LIGHT)
        p.line([(16,8),(19,8),(21,9)],BROWN_GLOW)
        p.poly([(28,14),(31,11),(37,11),(42,11),(48,14),(52,18),(54,22),(55,25),(53,28),(51,27),(46,26),(41,28),(37,30),(34,33),(31,37),(29,36),(27,32),(27,26),(25,22)],BROWN)
        p.poly([(30,14),(34,13),(39,13),(42,15),(39,18),(35,19),(31,22),(29,25),(28,23),(29,19)],BROWN_LIGHT)
        p.line([(25,11),(27,12),(29,10)],SHELL_TOP)
        p.poly([(35,33),(40,30),(43,29),(47,27),(51,28),(54,30),(53,36),(51,40),(47,43),(41,43),(37,42),(33,39),(33,36)],SKIN_SHADOW)
        p.poly([(35,34),(40,31),(44,31),(48,29),(51,30),(53,32),(52,37),(49,41),(46,42),(40,42),(36,40),(34,36)],SKIN)
        p.poly([(28,32),(31,31),(33,33),(33,36),(32,38),(29,37),(27,35)],SKIN_SHADOW)
        p.poly([(28,32),(30,32),(32,34),(31,36),(29,36),(28,35)],SKIN)
        p.line([(29,32),(29,34),(30,34)],SKIN_LIGHT)
        p.line([(37,32),(39,31),(41,32)],INK)
        p.line([(49,31),(51,31)],INK)
        p.poly([(36,34),(39,33),(42,34),(42,36),(40,38),(37,37)],PALE)
        p.poly([(38,33),(40,33),(41,34),(40,36),(39,36)],INK)
        p.poly([(47,33),(50,32),(52,33),(51,36),(50,37),(48,36)],PALE)
        p.line([(50,33),(50,35),(49,36)],INK)
        p.line([(45,36),(46,37),(45,38)],SKIN_LIGHT)
        p.line([(43,39),(45,40),(47,39)],INK)
        p.poly([(36,41),(40,42),(43,43),(43,46),(40,47),(37,45)],SKIN_SHADOW)
        p.line([(38,44),(41,45)],SKIN)
    return art


def cap(pose):
    art=layer()
    g,f=pose
    dx,dy=((1,1) if f else (0,0)) if not g else ((4,1) if f else (0,0))

    def place(x,y):
        # Keep the one-pixel outline at the brim tip through the passing pose.
        if x>54:
            x=54+round((x-54)*(.58 if g else .80))
        return x+dx,y+dy

    p=Pen(art,place)
    if not g:
        p.poly([(16,25),(18,19),(22,15),(27,11),(33,8),(41,7),(46,8),(50,10),(53,14),(54,18),(56,22),(59,24),(62,27),(61,28),(57,27),(53,26),(49,26),(42,25),(36,27),(29,28),(23,28),(19,27)],INK)
        p.poly([(18,24),(19,20),(23,16),(28,12),(34,9),(40,9),(46,10),(50,12),(52,16),(54,21),(53,23),(47,23),(41,23),(35,25),(29,26),(23,26),(20,26)],SHELL)
        p.poly([(41,9),(46,10),(49,12),(51,15),(53,20),(52,22),(47,22),(41,22),(37,23),(37,20),(39,15)],SHELL_LIGHT)
        p.line([(40,10),(38,15),(37,19),(36,23)],SHELL_TOP)
        p.line([(22,23),(25,24),(29,23)],REFLECTOR)
        p.poly([(38,24),(44,22),(51,22),(55,23),(59,25),(61,27),(57,26),(53,25),(46,24),(42,25)],SHELL_SHADOW)
        p.line([(44,23),(51,23),(55,24),(58,25)],SHELL_TOP)
    else:
        p.poly([(25,24),(26,19),(29,15),(33,12),(38,10),(43,10),(47,11),(51,13),(54,17),(55,21),(57,23),(60,26),(59,28),(55,27),(51,26),(46,25),(41,26),(36,27),(32,29),(28,28),(25,26)],INK)
        p.poly([(26,24),(28,18),(31,15),(35,12),(39,12),(44,12),(48,13),(51,16),(53,20),(54,22),(52,24),(46,23),(41,24),(36,25),(32,27),(28,26)],SHELL)
        p.poly([(41,12),(46,13),(50,16),(52,19),(53,22),(51,23),(45,23),(39,24),(36,25),(36,21),(38,16)],SHELL_LIGHT)
        p.line([(40,12),(38,17),(37,20),(36,24)],SHELL_TOP)
        p.line([(28,24),(30,25),(33,24)],REFLECTOR)
        p.poly([(37,25),(43,23),(48,23),(53,24),(56,25),(59,27),(55,26),(49,25),(44,25),(39,26)],SHELL_SHADOW)
        p.line([(43,24),(48,24),(53,25),(56,26)],SHELL_TOP)
    return art


SHIELD = 'Shield (optional)'


def shield(pose):
    art=layer()
    g,f=pose
    dx,dy=((1,1) if f else (0,0)) if not g else ((4,1) if f else (0,0))
    p=Pen(art,lambda x,y:(x+dx,y+dy))
    if g:
        p.line([(29,32),(35,31),(38,31)],SHELL_SHADOW)
        p.poly([(35,31),(42,30),(46,31),(52,30),(54,31),(53,35),(50,37),(47,36),(45,34),(42,36),(38,37),(35,35)],INK)
        p.poly([(36,32),(42,31),(46,32),(52,31),(53,32),(51,35),(49,35),(46,33),(42,34),(39,36),(37,35)],SHELL_LIGHT)
        p.line([(37,32),(41,32)],SOLE)
        p.line([(49,32),(51,32)],REFLECTOR)
    else:
        p.line([(26,35),(34,33),(37,33)],SHELL_SHADOW)
        p.poly([(34,33),(40,32),(45,33),(51,32),(54,34),(53,37),(50,39),(47,38),(45,36),(42,38),(38,39),(35,37)],INK)
        p.poly([(35,34),(40,33),(45,34),(51,33),(53,34),(51,37),(48,37),(45,35),(42,36),(38,38),(36,36)],SHELL_LIGHT)
        p.line([(36,34),(40,34)],SOLE)
        p.line([(48,34),(51,34)],REFLECTOR)
    return art


def fitted(pose):
    return (
        ('Far arm and leg',far_limbs(pose)),
        ('Near tights and shoe',near_leg(pose)),
        ('Shell and reflectors',jacket(pose)),
        ('Near elbow and hand',near_arm(pose)),
        ('Face and hair',face(pose)),
        ('Cap and brim',cap(pose)),
        (SHIELD,shield(pose)),
    )


def flatten(layers):
    out=layer()
    for name,image in layers:
        if name!=SHIELD:
            out.alpha_composite(image)
    return out


def compare(assets, compact, destination):
    from PIL import ImageDraw, ImageFont
    from export import preview

    night=('top-11','bottom-10','shoes-9','headwear-8')
    before={pose:preview(assets,pose,night) for pose in POSES}
    pale, dark='#e8ebe2','#263934'
    dark_ink, pale_ink, muted='#1e362e','#edf0e2','#536b5e'
    board=Image.new('RGB',(1176,941),'#f7f7ee')
    draw=ImageDraw.Draw(board)
    font_path='/usr/share/fonts/opentype/noto/NotoSansCJK-{}.ttc'
    size_fonts={}

    def text(x,y,label,size=18,color=dark_ink,bold=False):
        font_key=size,bold
        if font_key not in size_fonts:
            size_fonts[font_key]=ImageFont.truetype(font_path.format('Bold' if bold else 'Regular'),size,index=1)
        draw.text((x,y),label,font=size_fonts[font_key],fill=color)

    def cell(image,x,y,width,height,canvas_width,canvas_height,background):
        draw.rounded_rectangle((x,y,x+canvas_width-1,y+canvas_height-1),radius=4,fill=background)
        fitted_image=image.resize((width,height),Image.Resampling.NEAREST)
        board.paste(fitted_image,(x+(canvas_width-width)//2,y+(canvas_height-height)//2),fitted_image)

    text(28,14,'나이트 착장 도트 비교',38,bold=True)
    text(30,68,'같은 얼굴과 보폭을 64×96 격자에 다시 그렸습니다. 1dp를 1px로 맞춘 옷장 크기입니다.',19,muted)
    for g in range(2):
        for newer in range(2):
            x=28+(2*g+newer)*284
            shade='#4e7569' if newer else '#66776d'
            draw.line((x,117,x+264,117),fill=shade,width=3)
            model='남자' if g==0 else '여자'
            variant='64×96 시안' if newer else '현재 원화'
            text(x,128,f'{model} · {variant}',23,bold=True)
            a,b=(compact[(g,0)],compact[(g,1)]) if newer else (before[(g,0)],before[(g,1)])
            text(x,173,'전체 96dp',17,muted,True)
            for i,bg in enumerate((pale,dark)):
                cell(a,x+i*135,201,64,96,128,107,bg)
            text(x,324,'전체 144dp',17,muted,True)
            for i,bg in enumerate((pale,dark)):
                cell(a,x+i*135,354,96,144,128,159,bg)
            text(x,528,'목록 64dp · 모자와 상의',17,muted,True)
            tile_scale=1 if newer else 4
            face=(0,0,64,52)
            top=(10,39 if g else 42,60,67 if g else 70)
            for i,(region,height) in enumerate(((face,52),(top,36))):
                cut=a.crop(tuple(n*tile_scale for n in region))
                for j,bg in enumerate((pale,dark)):
                    cell(cut,x+(i*2+j)*67,558,64,height,64,64,bg)
            text(x,649,'팔이 앞으로 오는 포즈',17,muted,True)
            for i,bg in enumerate((pale,dark)):
                cell(b,x+i*135,679,96,144,128,159,bg)
    text(30,875,'밝은 배경과 어두운 배경 · 최근접 확대 · 실드 미착용',18,muted)
    text(30,903,'보폭과 팔 가림을 확인한 네 컷입니다. 전체 러닝 동작과 다른 의상 착용은 따로 확인해야 합니다.',16,muted)
    board.save(destination,optimize=True)


def export_candidate(binary, output):
    from pathlib import Path
    from subprocess import run
    from tempfile import TemporaryDirectory
    from export import ase_project, details_from_sheet, same_visible
    from paint import ASSET

    output.mkdir(parents=True,exist_ok=True)
    tags=(('Male pose study',0,1,(68,147,126)),('Female pose study',2,3,(190,123,117)))
    poses=[fitted(pose) for pose in POSES]
    with TemporaryDirectory(prefix='runmoni-pilot-') as work:
        work=Path(work)
        draft=work/'compact-night-draft.aseprite'
        stage=work/'compact-night.aseprite'
        draft.write_bytes(ase_project(poses,SIZE,tags,hidden_layers=(SHIELD,)))
        run([binary,'-b',str(draft),'--save-as',str(stage)],check=True)
        sheet=work/'compact-night.png'; metadata=work/'compact-night.json'
        run([binary,'-b',str(stage),'--sheet',str(sheet),'--data',str(metadata)],check=True)
        rendered=dict(zip(POSES,details_from_sheet(sheet,metadata,SIZE,len(POSES)),strict=True))
        for pose,layers in zip(POSES,poses,strict=True):
            assert same_visible(rendered[pose],flatten(layers)),f'Aseprite pilot rendering changed {pose}'
            assert cap(pose).getbbox()[2]<SIZE[0],f'Pilot brim reached canvas edge {pose}'
            assert SHIELD in [name for name,image in layers if image.getbbox()]
        compare(ASSET/'atelier-v3',rendered,output/'compact-night-comparison.png')
        (output/'compact-night.aseprite').write_bytes(stage.read_bytes())
    palette={pixel[:3] for im in rendered.values() for pixel in im.get_flattened_data() if pixel[3]}
    print(f'{len(rendered)} manually fitted 64×96 Aseprite proof cels, {len(palette)} RGB values across all poses')


if __name__=='__main__':
    from argparse import ArgumentParser
    from pathlib import Path

    parser=ArgumentParser(description=__doc__)
    parser.add_argument('--aseprite',required=True)
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    export_candidate(args.aseprite,args.output)
