; ----------------------------------------
; Name : Terrain The Voxel Space engine
; Date : (C)2025
; Site : https://github.com/BlackCreepyCat
; Inspired from : 
; https://www.youtube.com/watch?v=bQBY9BM9g_Y
; ----------------------------------------

; Set the screen resolution to 800x600, 32-bit color depth, and double buffering
Graphics3D 800, 600, 32, 2
SetBuffer BackBuffer()

; Define constants for screen dimensions and map size
Global SCREEN_WIDTH = GraphicsWidth()
Global SCREEN_HEIGHT = GraphicsHeight()
Const MAP_N = 1024
Const SCALE_FACTOR# = 70.0
Const CAMERA_SMOOTHING# = 0.1  ; Smoothing factor for camera motion
Const HEIGHT_SCALE_FACTOR# = 2.0  ; Augmenter la hauteur des montagnes

; Declare arrays to store height and color maps
Dim heightmap(MAP_N-1, MAP_N-1)
Dim colormap(MAP_N-1, MAP_N-1)

; Load the height and color images
Global heightImage = LoadImage("map17.height.png")
Global colorImage = LoadImage("map17.color.png")

; Check if the images were successfully loaded
If heightImage = 0 Or colorImage = 0 Then
    RuntimeError "Error: Unable to load heightmap or colormap!"
EndIf

; Check if the images have the correct dimensions
If ImageWidth(heightImage) <> MAP_N Or ImageHeight(heightImage) <> MAP_N Then
    RuntimeError "Error: Incorrect image size!"
EndIf

; Lock the image buffers to read the pixel data
LockBuffer ImageBuffer(heightImage)
LockBuffer ImageBuffer(colorImage)

For x = 0 To MAP_N-1
    For y = 0 To MAP_N-1
		
        ; Read pixel data for heightmap and colormap
        heightmap(x, y) = ReadPixelFast(x, y, ImageBuffer(heightImage)) And $FF
        colormap(x, y) = ReadPixelFast(x, y, ImageBuffer(colorImage))
        
        ; Appliquer le facteur de mise à l'échelle de la hauteur
        heightmap(x, y) = Int(heightmap(x, y) * HEIGHT_SCALE_FACTOR#)  ; Multiplier la hauteur
    Next
Next

UnlockBuffer ImageBuffer(heightImage)
UnlockBuffer ImageBuffer(colorImage)

; Free the images after reading their data
FreeImage heightImage
FreeImage colorImage

; Define the Camera type with fields for position, height, angle, etc.
Type Camera
    Field x#, y#, height#, horizon#, zfar#, angle#
    Field targetX#, targetY#, targetHeight#, targetAngle#
End Type

; Initialize the camera object with default values
Global cam.Camera = New Camera
cam\x = 512.0
cam\y = 512.0
cam\height = 120.0
cam\horizon = 60.0
cam\zfar = 700.0
cam\angle = 0

; Set initial target values to match the current camera position
cam\targetX = cam\x
cam\targetY = cam\y
cam\targetHeight = cam\height
cam\targetAngle = cam\angle

; Create a framebuffer image for drawing
Global framebuffer = CreateImage(SCREEN_WIDTH, SCREEN_HEIGHT)

MoveMouse(SCREEN_WIDTH/2,SCREEN_HEIGHT/2)

; Main game loop that runs until the escape key is pressed
While Not KeyHit(1)
    Local moveSpeed# = 2.0
    
    ; Check for movement input and update target camera position
    If KeyDown(200) Then 
        cam\targetX = cam\targetX + Cos(cam\angle) * moveSpeed
        cam\targetY = cam\targetY + Sin(cam\angle) * moveSpeed
    EndIf
    If KeyDown(208) Then 
        cam\targetX = cam\targetX - Cos(cam\angle) * moveSpeed
        cam\targetY = cam\targetY - Sin(cam\angle) * moveSpeed
    EndIf
    
    ; Gestion de la rotation horizontale avec la souris
    Local mouseSensitivity# = 0.2  ; Sensibilité de la souris (ajustable)
    cam\targetAngle = cam\targetAngle + MouseXSpeed() * mouseSensitivity  ; Rotation horizontale
    
    ; Option 2 : Contrôler l'horizon (cam\horizon) avec la souris
    cam\horizon = cam\horizon - MouseYSpeed() * mouseSensitivity * 10  ; Inclinaison haut/bas
    
    ; Optionnel : recentrer la souris au milieu de l'écran pour une rotation continue
    MoveMouse SCREEN_WIDTH / 2, SCREEN_HEIGHT / 2
    
    ; Adjust the target height or horizon based on keyboard input (optionnel)
    If KeyDown(18) Then cam\targetHeight = cam\targetHeight + 10 ; E
    If KeyDown(32) Then cam\targetHeight = cam\targetHeight - 10 ; D
    
    If KeyDown(31) Then cam\horizon = cam\horizon + 10.5 ; S
    If KeyDown(17) Then cam\horizon = cam\horizon - 10.5 ; W
    
    ; Smooth the camera movement and rotation using linear interpolation
    cam\x = cam\x + (cam\targetX - cam\x) * CAMERA_SMOOTHING
    cam\y = cam\y + (cam\targetY - cam\y) * CAMERA_SMOOTHING
    cam\height = cam\height + (cam\targetHeight - cam\height) * CAMERA_SMOOTHING
    cam\angle = cam\angle + (cam\targetAngle - cam\angle) * CAMERA_SMOOTHING
	
	
    ; (Le reste de la boucle principale reste inchangé jusqu'à l'affichage du texte)
    ; Set the buffer for drawing to the framebuffer
    SetBuffer ImageBuffer(framebuffer)
    ClsColor 50, 100, 200
    Cls
    
    ; Switch back to the back buffer and lock the framebuffer buffer
    SetBuffer BackBuffer()
    LockBuffer ImageBuffer(framebuffer)
    
    ; Calculate the camera's direction using sine and cosine
    Local sinangle# = Sin(cam\angle)
    Local cosangle# = Cos(cam\angle)
    
    ; Calculate the four corners of the viewing plane
    Local plx# = cosangle * cam\zfar + sinangle * cam\zfar
    Local ply# = sinangle * cam\zfar - cosangle * cam\zfar
    Local prx# = cosangle * cam\zfar - sinangle * cam\zfar
    Local pry# = sinangle * cam\zfar + cosangle * cam\zfar
	
    ; Loop through each horizontal screen pixel
    For i = 0 To SCREEN_WIDTH-1
        ; Calculate the position for each pixel on the map
        Local deltax# = (plx + (prx - plx) / SCREEN_WIDTH * i) / cam\zfar
        Local deltay# = (ply + (pry - ply) / SCREEN_WIDTH * i) / cam\zfar
        
        ; Initialize camera position and set the tallest height to the screen height
        Local rx# = cam\x
        Local ry# = cam\y
        Local tallestheight# = Float(SCREEN_HEIGHT)
        
        Local z# = 1.0
        Local stepZ# = 1.0
        
        ; Loop through each pixel in the view range, adjusting the z-buffer
        While z < cam\zfar
            rx = rx + deltax
            ry = ry + deltay
            
            ; Get the map coordinates and height value at the current position
            Local mapX = Int(rx) And (MAP_N-1)
            Local mapY = Int(ry) And (MAP_N-1)
            Local h# = Float(heightmap(mapX, mapY))  
            Local projheight# = (cam\height - h) / (z + 0.0001) * SCALE_FACTOR + cam\horizon  
            
            ; Draw pixels if the projected height is less than the tallest height
            If projheight < tallestheight Then
                For y = projheight To tallestheight-1
					
                    If y >= 0 And y < SCREEN_HEIGHT Then
						WritePixelFast i, y-1, colormap(mapX, mapY), ImageBuffer(framebuffer)
                        WritePixelFast i, y, colormap(mapX, mapY), ImageBuffer(framebuffer)
                    EndIf
					
                Next
                tallestheight = projheight
            EndIf
            
            ; Increase stepZ for faster movement at greater distances
            If z > cam\zfar / 2 Then stepZ = 2.0 Else stepZ = 1.0
            
            ; Increment z to move further into the scene
            z = z + stepZ
        Wend
    Next
    
    ; Unlock the framebuffer buffer
    UnlockBuffer ImageBuffer(framebuffer)
    
    ; Draw the framebuffer to the screen and flip buffers
    DrawImage framebuffer, 0, 0
	
	Color 0,0,0
	Text 10,10," ARROWS TO MOVE / Z&S TO ROTATE / E&D TO UP DOWN
    Flip
    
Wend


; Free the framebuffer image after use
FreeImage framebuffer
End

;~IDEal Editor Parameters:
;~C#Blitz3D