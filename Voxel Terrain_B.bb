; ----------------------------------------
; Name : Terrain The Voxel Space engine
; Date : (C)2025
; ----------------------------------------

Graphics3D 800, 600, 32, 2
SetBuffer BackBuffer()

Global SCREEN_WIDTH = 800
Global SCREEN_HEIGHT = 600

Const MAP_N = 1024
Const SCALE_FACTOR# = 70.0
Const CAMERA_SMOOTHING# = 0.1
Const HEIGHT_SCALE_FACTOR# = 2.0

Dim heightmap(MAP_N-1, MAP_N-1)
Dim colormap(MAP_N-1, MAP_N-1)

num$="16"

Global heightImage = LoadImage("map"+num$+".height.png")
Global colorImage = LoadImage("map"+num$+".Color.png")

If heightImage = 0 Or colorImage = 0 Then
    RuntimeError "Error: Unable to load heightmap or colormap!"
EndIf

If ImageWidth(heightImage) <> MAP_N Or ImageHeight(heightImage) <> MAP_N Then
    RuntimeError "Error: Incorrect image size!"
EndIf

LockBuffer ImageBuffer(heightImage)
LockBuffer ImageBuffer(colorImage)

For x = 0 To MAP_N-1
    For y = 0 To MAP_N-1
        heightmap(x, y) = ReadPixelFast(x, y, ImageBuffer(heightImage)) And $FF
        colormap(x, y) = ReadPixelFast(x, y, ImageBuffer(colorImage))
        heightmap(x, y) = Int(heightmap(x, y) * HEIGHT_SCALE_FACTOR#)
    Next
Next

UnlockBuffer ImageBuffer(heightImage)
UnlockBuffer ImageBuffer(colorImage)
FreeImage heightImage
FreeImage colorImage

Type Camera
    Field x#, y#, height#, horizon#, zfar#, angle#, pitch#
    Field targetX#, targetY#, targetHeight#, targetAngle#, targetPitch#
End Type

Global cam.Camera = New Camera
cam\x = 512.0
cam\y = 512.0
cam\height = 120.0
cam\horizon = 60.0
cam\zfar = 700.0
cam\angle = 0
cam\pitch = 0

cam\targetX = cam\x
cam\targetY = cam\y
cam\targetHeight = cam\height
cam\targetAngle = cam\angle
cam\targetPitch = cam\pitch

Global framebuffer = CreateImage(SCREEN_WIDTH, SCREEN_HEIGHT)

MoveMouse(SCREEN_WIDTH/2, SCREEN_HEIGHT/2)

While Not KeyHit(1)
    Local moveSpeed# = 2.0
    Local mouseSensitivity# = 0.2  ; Sensibilité pour la rotation
    Local pitchHeightMultiplier# = 5.0  ; Multiplicateur pour amplifier l'effet du pitch sur la hauteur
    
    ; Rotation horizontale (yaw) avec la souris, inversée
    cam\targetAngle = cam\targetAngle + MouseXSpeed() * mouseSensitivity  ; Inversion : + au lieu de - 
    
    ; Inclinaison verticale (pitch) avec la souris
    cam\targetPitch = cam\targetPitch - MouseYSpeed() * mouseSensitivity
    
    ; Limiter le pitch entre -89° et 89° pour éviter de basculer
    If cam\targetPitch > 89 Then cam\targetPitch = 89
    If cam\targetPitch < -89 Then cam\targetPitch = -89
    
    ; Recentrer la souris
    MoveMouse SCREEN_WIDTH / 2, SCREEN_HEIGHT / 2
    
    ; Calculer les composantes de mouvement basées sur l'angle et le pitch
    Local sinPitch# = Sin(cam\pitch)
    Local cosPitch# = Cos(cam\pitch)
    Local sinAngle# = Sin(cam\angle)
    Local cosAngle# = Cos(cam\angle)
    
    ; Mouvement contrôlé par les flèches (gauche/droite non inversé)
    If KeyDown(200) Then  ; Haut (avancer)
        cam\targetX = cam\targetX + cosAngle * cosPitch * moveSpeed
        cam\targetY = cam\targetY + sinAngle * cosPitch * moveSpeed
        cam\targetHeight = cam\targetHeight + sinPitch * moveSpeed * pitchHeightMultiplier
    EndIf
    If KeyDown(208) Then  ; Bas (reculer)
        cam\targetX = cam\targetX - cosAngle * cosPitch * moveSpeed
        cam\targetY = cam\targetY - sinAngle * cosPitch * moveSpeed
        cam\targetHeight = cam\targetHeight - sinPitch * moveSpeed * pitchHeightMultiplier
    EndIf
    
    If KeyDown(203) Then  ; Gauche (strafe vers la gauche)
        cam\targetX = cam\targetX + sinAngle * moveSpeed
        cam\targetY = cam\targetY - cosAngle * moveSpeed
    EndIf
    If KeyDown(205) Then  ; Droite (strafe vers la droite)
        cam\targetX = cam\targetX - sinAngle * moveSpeed
        cam\targetY = cam\targetY + cosAngle * moveSpeed
    EndIf
    
    ; Ajuster l'horizon en fonction du pitch pour l'effet visuel
    cam\horizon = 60.0 + (cam\pitch * 5.0)  ; 60 est la valeur de base, ajustée par le pitch
    
    ; Limiter la hauteur pour éviter de descendre sous le sol
    If cam\targetHeight < 1 Then cam\targetHeight = 1
    
    ; Calculer la hauteur du terrain sous la caméra pour détecter la collision avec le sol
    Local mapX = Int(cam\targetX) And (MAP_N-1)
    Local mapY = Int(cam\targetY) And (MAP_N-1)
	
    Local terrainHeight# = Float(heightmap(mapX, mapY))
    
    ; Si la caméra descend sous la hauteur du terrain, la corriger
    If cam\targetHeight < TerrainHeight Then
        cam\targetHeight = TerrainHeight + 2  ; Décalage léger pour éviter que la caméra ne passe à travers le sol
    EndIf
    
    ; Lissage des mouvements et rotations
    cam\x = cam\x + (cam\targetX - cam\x) * CAMERA_SMOOTHING
    cam\y = cam\y + (cam\targetY - cam\y) * CAMERA_SMOOTHING
    
    cam\height = cam\height + (cam\targetHeight - cam\height) * CAMERA_SMOOTHING
    cam\angle = cam\angle + (cam\targetAngle - cam\angle) * CAMERA_SMOOTHING
    cam\pitch = cam\pitch + (cam\targetPitch - cam\pitch) * CAMERA_SMOOTHING
    
    SetBuffer ImageBuffer(framebuffer)
    ClsColor 50, 100, 200
    Cls
    
    SetBuffer BackBuffer()
    LockBuffer ImageBuffer(framebuffer)
    
    sinAngle# = Sin(cam\angle)
    cosAngle# = Cos(cam\angle)
    
    Local plx# = cosAngle * cam\zfar + sinAngle * cam\zfar
    Local ply# = sinAngle * cam\zfar - cosAngle * cam\zfar
    Local prx# = cosAngle * cam\zfar - sinAngle * cam\zfar
    Local pry# = sinAngle * cam\zfar + cosAngle * cam\zfar
    
    For i = 0 To SCREEN_WIDTH-1
        Local deltax# = (plx + (prx - plx) / SCREEN_WIDTH * i) / cam\zfar
        Local deltay# = (ply + (pry - ply) / SCREEN_WIDTH * i) / cam\zfar
        
        Local rx# = cam\x
        Local ry# = cam\y
        Local tallestheight# = Float(SCREEN_HEIGHT)
        
        Local z# = 1.0
        Local stepZ# = 1.0
        
        While z < cam\zfar
            rx = rx + deltax
            ry = ry + deltay
            
			mapX = Int(rx) And (MAP_N-1)
            mapY = Int(ry) And (MAP_N-1)
			
            Local h# = Float(heightmap(mapX, mapY))
            Local projheight# = (cam\height - h) / (z + 0.0001) * SCALE_FACTOR + cam\horizon
            
            If projheight < tallestheight Then
                For y = projheight To tallestheight-1
					
                    If y >= 0 And y < SCREEN_HEIGHT Then
                        WritePixelFast i, y-1, colormap(mapX, mapY), ImageBuffer(framebuffer)
                        WritePixelFast i, y, colormap(mapX, mapY), ImageBuffer(framebuffer)
                    EndIf
					
                Next
                tallestheight = projheight
            EndIf
            
            If z > cam\zfar / 2 Then stepZ = 2.0 Else stepZ = 1.0
            z = z + stepZ
        Wend
    Next
    
    UnlockBuffer ImageBuffer(framebuffer)
    
    DrawImage framebuffer, 0, 0
    
    Color 0, 0, 0
    Text 10, 10, "ARROWS TO MOVE / MOUSE FOR YAW (INVERTED) & PITCH"
    Flip
Wend

FreeImage framebuffer
End
