import * as THREE from 'three'

const app = document.getElementById('app')
const overlay = document.getElementById('overlay')
const hotbar = document.getElementById('hotbar')
// HUD elements
const hud = document.createElement('div')
hud.style.position = 'absolute'
hud.style.left = '16px'
hud.style.bottom = '16px'
hud.style.width = '220px'
hud.style.height = '14px'
hud.style.border = '2px solid #fff3'
hud.style.background = '#0006'
document.body.appendChild(hud)
const hpBar = document.createElement('div')
hpBar.style.height = '100%'
hpBar.style.width = '100%'
hpBar.style.background = '#e33'
hud.appendChild(hpBar)
const crosshair = document.createElement('div')
crosshair.style.position = 'absolute'
crosshair.style.left = '50%'
crosshair.style.top = '50%'
crosshair.style.transform = 'translate(-50%,-50%)'
crosshair.style.width = '10px'
crosshair.style.height = '10px'
crosshair.style.border = '2px solid white'
crosshair.style.borderRadius = '50%'
crosshair.style.opacity = '0'
document.body.appendChild(crosshair)

// Three.js setup
const renderer = new THREE.WebGLRenderer({ antialias: false, powerPreference: 'high-performance' })
renderer.outputColorSpace = THREE.SRGBColorSpace
let currentDPR = Math.min(window.devicePixelRatio || 1, 1.25)
const targetDPR = currentDPR
renderer.setPixelRatio(targetDPR)
renderer.setSize(window.innerWidth, window.innerHeight)
app.appendChild(renderer.domElement)

const scene = new THREE.Scene()
scene.background = new THREE.Color('#87ceeb')

const camera = new THREE.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.1, 1000)
scene.add(camera)
let thirdPerson = false
const thirdOffset = new THREE.Vector3(0, 2.2, 3.5)
const thirdMinY = 0.4
// First-person hands + gun group (attached to camera)
const fpGroup = new THREE.Group()
fpGroup.position.set(0.35, -0.35, -0.8)

// Enhanced first-person arm
const fpArmGeom = new THREE.BoxGeometry(0.12, 0.4, 0.12)
const fpArmMat = new THREE.MeshStandardMaterial({ 
  color: 0xffd7b3, 
  roughness: 0.8, 
  metalness: 0.0 
})
const fpRightArm = new THREE.Mesh(fpArmGeom, fpArmMat)
fpRightArm.position.set(0, -0.2, 0)
fpRightArm.castShadow = true

// Enhanced first-person gun
const fpGunGroup = new THREE.Group()

// Main gun body
const fpGunBodyGeom = new THREE.BoxGeometry(0.15, 0.08, 0.4)
const fpGunBodyMat = new THREE.MeshStandardMaterial({ 
  color: 0x2a2a2a, 
  roughness: 0.3, 
  metalness: 0.8 
})
const fpGunBody = new THREE.Mesh(fpGunBodyGeom, fpGunBodyMat)
fpGunBody.position.set(0.05, -0.05, -0.1)
fpGunGroup.add(fpGunBody)

// Gun barrel
const fpBarrelGeom = new THREE.CylinderGeometry(0.025, 0.025, 0.25, 8)
const fpBarrelMat = new THREE.MeshStandardMaterial({ 
  color: 0x1a1a1a, 
  roughness: 0.2, 
  metalness: 0.9 
})
const fpBarrel = new THREE.Mesh(fpBarrelGeom, fpBarrelMat)
fpBarrel.rotation.z = Math.PI / 2
fpBarrel.position.set(0.05, -0.05, -0.3)
fpGunGroup.add(fpBarrel)

// Gun handle
const fpHandleGeom = new THREE.BoxGeometry(0.08, 0.15, 0.1)
const fpHandleMat = new THREE.MeshStandardMaterial({ 
  color: 0x8b4513, 
  roughness: 0.8, 
  metalness: 0.0 
})
const fpHandle = new THREE.Mesh(fpHandleGeom, fpHandleMat)
fpHandle.position.set(0.05, -0.15, 0)
fpGunGroup.add(fpHandle)

// Trigger guard
const fpGuardGeom = new THREE.TorusGeometry(0.05, 0.015, 4, 8, Math.PI)
const fpGuardMat = new THREE.MeshStandardMaterial({ 
  color: 0x2a2a2a, 
  roughness: 0.3, 
  metalness: 0.8 
})
const fpGuard = new THREE.Mesh(fpGuardGeom, fpGuardMat)
fpGuard.rotation.x = Math.PI / 2
fpGuard.position.set(0.05, -0.1, 0)
fpGunGroup.add(fpGuard)

fpGunGroup.position.set(0.05, -0.05, -0.2)

fpGroup.add(fpRightArm)
fpGroup.add(fpGunGroup)
camera.add(fpGroup)
fpGroup.visible = false
let fpRecoil = 0

// Enhanced lighting system
const ambient = new THREE.AmbientLight(0x87ceeb, 0.3) // Sky blue ambient
scene.add(ambient)

// Main sun light
const sun = new THREE.DirectionalLight(0xfff8dc, 0.8) // Warm sunlight
sun.position.set(0.5, 1, 0.3)
sun.castShadow = true
sun.shadow.mapSize.width = 2048
sun.shadow.mapSize.height = 2048
sun.shadow.camera.near = 0.5
sun.shadow.camera.far = 200
sun.shadow.camera.left = -50
sun.shadow.camera.right = 50
sun.shadow.camera.top = 50
sun.shadow.camera.bottom = -50
sun.shadow.bias = -0.0001
scene.add(sun)

// Fill light for better illumination
const fillLight = new THREE.DirectionalLight(0x4a90e2, 0.2)
fillLight.position.set(-0.5, 0.5, -0.3)
scene.add(fillLight)

// Rim light for better depth
const rimLight = new THREE.DirectionalLight(0xffffff, 0.15)
rimLight.position.set(0, 0.8, 1)
scene.add(rimLight)

// Ground plane (grass style)
const groundGeom = new THREE.PlaneGeometry(1200, 1200, 1, 1)
groundGeom.rotateX(-Math.PI / 2)
function createGrassTexture() {
  const size = 256 // Higher resolution
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  
  // Base grass color
  ctx.fillStyle = '#5a9a4a'
  ctx.fillRect(0, 0, size, size)
  
  // Add grass blade patterns
  ctx.strokeStyle = '#4a8a3a'
  ctx.lineWidth = 1
  for (let i = 0; i < 200; i++) {
    const x = Math.random() * size
    const y = Math.random() * size
    const height = Math.random() * 8 + 2
    const angle = (Math.random() - 0.5) * 0.3
    ctx.beginPath()
    ctx.moveTo(x, y)
    ctx.lineTo(x + Math.sin(angle) * height, y - height)
    ctx.stroke()
  }
  
  // Add dirt patches
  ctx.fillStyle = '#8b4513'
  for (let i = 0; i < 30; i++) {
    const x = Math.random() * size
    const y = Math.random() * size
    const r = Math.random() * 4 + 1
    ctx.beginPath()
    ctx.arc(x, y, r, 0, Math.PI * 2)
    ctx.fill()
  }
  
  // Add flower spots
  ctx.fillStyle = '#ff69b4'
  for (let i = 0; i < 20; i++) {
    const x = Math.random() * size
    const y = Math.random() * size
    const r = Math.random() * 1.5 + 0.5
    ctx.beginPath()
    ctx.arc(x, y, r, 0, Math.PI * 2)
    ctx.fill()
  }
  
  const tex = new THREE.CanvasTexture(canvas)
  tex.wrapS = THREE.RepeatWrapping
  tex.wrapT = THREE.RepeatWrapping
  tex.magFilter = THREE.LinearFilter
  tex.minFilter = THREE.LinearMipmapLinearFilter
  tex.repeat.set(256, 256)
  return tex
}
const groundMat = new THREE.MeshLambertMaterial({ map: createGrassTexture() })
const ground = new THREE.Mesh(groundGeom, groundMat)
ground.receiveShadow = false
scene.add(ground)

// Player rigs and blocks
const playerRigs = new Map()
let myId = null
// All blocks known from server (lazy-load meshes near camera only)
const allBlocks = new Map() // key -> type
const blockMeshes = new Map() // key -> mesh (only nearby)
const blockMaterials = new Map() // type -> material
const VISIBLE_RADIUS = 72
// Instanced rendering state
const instancedByType = new Map() // type -> InstancedMesh
const instanceKeysByType = new Map() // type -> [key]
const tmpMatrix = new THREE.Matrix4()
const frustum = new THREE.Frustum()
const projScreenMatrix = new THREE.Matrix4()
let selectedType = 1
let selectedKind = 'block'
const raycaster = new THREE.Raycaster()
raycaster.far = 48
const groundPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0)
const npcRigs = new Map() // id -> rig
const lastShotById = new Map() // id -> timestamp ms

// Death overlay
const deathOverlay = document.createElement('div')
deathOverlay.style.position = 'absolute'
deathOverlay.style.inset = '0'
deathOverlay.style.display = 'none'
deathOverlay.style.background = 'rgba(0,0,0,0.5)'
deathOverlay.style.color = 'white'
deathOverlay.style.fontFamily = 'system-ui, sans-serif'
deathOverlay.style.display = 'grid'
deathOverlay.style.placeItems = 'center'
deathOverlay.innerHTML = '<div style="text-align:center"><div style="font-size:28px;font-weight:700">You Died</div><div style="margin-top:8px;font-size:14px;opacity:0.8">Click to respawn</div></div>'
document.body.appendChild(deathOverlay)

function blockKey(x, y, z) { return `${x},${y},${z}` }
function dist2xz(ax, az, bx, bz) { const dx = ax - bx, dz = az - bz; return dx*dx + dz*dz }
function isWithinRadius(x, z) {
  const cx = camera.position.x || 0, cz = camera.position.z || 0
  return dist2xz(x + 0.5, z + 0.5, cx, cz) <= VISIBLE_RADIUS * VISIBLE_RADIUS
}

function createHumanRig(palette, isPolice = false) {
  const group = new THREE.Group()
  const legsH = 0.8, bodyH = 0.8, headH = 0.25
  const legW = 0.22, legD = 0.22
  const bodyW = 0.5, bodyD = 0.28
  const armW = 0.18, armD = 0.18, armH = 0.8
  
  // Enhanced materials with better properties
  const skinMat = new THREE.MeshStandardMaterial({ 
    color: palette.skin,
    roughness: 0.8,
    metalness: 0.0
  })
  const shirtMat = new THREE.MeshStandardMaterial({ 
    color: palette.shirt,
    roughness: 0.7,
    metalness: 0.0
  })
  const pantsMat = new THREE.MeshStandardMaterial({ 
    color: palette.pants,
    roughness: 0.8,
    metalness: 0.0
  })
  
  // Create legs with more detail
  const legGeom = new THREE.BoxGeometry(legW, legsH, legD)
  const leftLeg = new THREE.Mesh(legGeom, pantsMat)
  const rightLeg = new THREE.Mesh(legGeom, pantsMat)
  const legOffsetX = (legW / 2) + 0.05
  leftLeg.position.set(-legOffsetX, legsH / 2, 0)
  rightLeg.position.set(legOffsetX, legsH / 2, 0)
  leftLeg.castShadow = true
  rightLeg.castShadow = true
  
  // Enhanced body with belt and details
  const bodyGeom = new THREE.BoxGeometry(bodyW, bodyH, bodyD)
  const body = new THREE.Mesh(bodyGeom, shirtMat)
  body.position.set(0, legsH + bodyH / 2, 0)
  body.castShadow = true
  
  // Add belt for police
  if (isPolice) {
    const beltGeom = new THREE.BoxGeometry(bodyW + 0.1, 0.08, bodyD + 0.1)
    const beltMat = new THREE.MeshStandardMaterial({ color: 0x2a2a2a, roughness: 0.3, metalness: 0.8 })
    const belt = new THREE.Mesh(beltGeom, beltMat)
    belt.position.set(0, legsH + bodyH * 0.3, 0)
    body.add(belt)
  }
  
  // Enhanced arms
  const armGeom = new THREE.BoxGeometry(armW, armH, armD)
  const leftArm = new THREE.Mesh(armGeom, skinMat)
  const rightArm = new THREE.Mesh(armGeom, skinMat)
  const armOffsetX = (bodyW / 2) + (armW / 2)
  const armY = legsH + armH / 2
  leftArm.position.set(-armOffsetX, armY, 0)
  rightArm.position.set(armOffsetX, armY, 0)
  leftArm.castShadow = true
  rightArm.castShadow = true
  
  // Enhanced head with hat for police
  const headGeom = new THREE.BoxGeometry(0.5, headH, 0.5)
  const head = new THREE.Mesh(headGeom, skinMat)
  head.position.set(0, legsH + bodyH + headH / 2, 0)
  head.castShadow = true
  
  // Add police hat
  if (isPolice) {
    const hatGeom = new THREE.CylinderGeometry(0.3, 0.35, 0.15, 8)
    const hatMat = new THREE.MeshStandardMaterial({ color: 0x1a1a1a, roughness: 0.4, metalness: 0.2 })
    const hat = new THREE.Mesh(hatGeom, hatMat)
    hat.position.set(0, headH / 2 + 0.08, 0)
    head.add(hat)
    
    // Add police badge
    const badgeGeom = new THREE.BoxGeometry(0.08, 0.06, 0.02)
    const badgeMat = new THREE.MeshStandardMaterial({ color: 0xffd700, roughness: 0.2, metalness: 0.9 })
    const badge = new THREE.Mesh(badgeGeom, badgeMat)
    badge.position.set(0.15, 0.1, 0.2)
    body.add(badge)
  }
  
  group.add(leftLeg, rightLeg, body, leftArm, rightArm, head)
  
  const rig = { 
    group, 
    parts: { leftLeg, rightLeg, leftArm, rightArm, head, body }, 
    lastPos: new THREE.Vector3(), 
    walkPhase: 0,
    isPolice: isPolice,
    shooting: false,
    shootTime: 0,
    shootDuration: 1.0, // Duration of shooting animation in seconds
    update(pos, yaw, pitch, dt) {
      const dx = pos.x - this.lastPos.x, dz = pos.z - this.lastPos.z
      const speed = Math.min(1.0, Math.hypot(dx, dz) / Math.max(0.0001, dt))
      this.walkPhase += speed * dt * 6.0
      
      // Update shooting animation
      if (this.shooting) {
        this.shootTime += dt
        if (this.shootTime >= this.shootDuration) {
          this.shooting = false
          this.shootTime = 0
        }
      }
      
      // Enhanced walking animation with more realistic movement
      const swing = Math.sin(this.walkPhase) * 0.6 * speed
      const bodyBob = Math.abs(Math.sin(this.walkPhase * 2)) * 0.05 * speed
      
      // Apply walking animation only when not shooting
      if (!this.shooting) {
        this.parts.leftLeg.rotation.x = swing
        this.parts.rightLeg.rotation.x = -swing
        this.parts.leftArm.rotation.x = -swing * 0.8
        this.parts.rightArm.rotation.x = swing * 0.8
        
        // Reset gun glow when not shooting
        if (this.parts._permanentGun) {
          this.parts._permanentGun.traverse((child) => {
            if (child.isMesh && child.material) {
              child.material.emissive = new THREE.Color(0x000000) // No glow
            }
          })
        }
      } else {
        // Shooting animation - raise gun and aim
        const shootProgress = this.shootTime / this.shootDuration
        const aimHeight = Math.sin(shootProgress * Math.PI) * 1.2 // Even more pronounced animation
        
        // Raise right arm to aim (very dramatic)
        this.parts.rightArm.rotation.x = -Math.PI/2 + aimHeight * 0.8
        this.parts.rightArm.rotation.z = aimHeight * 0.5
        
        // Slight body lean forward when aiming
        this.parts.body.rotation.x = aimHeight * 0.15
        
        // Left arm supports the gun (very pronounced)
        this.parts.leftArm.rotation.x = -Math.PI/2.2 + aimHeight * 0.5
        this.parts.leftArm.rotation.z = -aimHeight * 0.4
        
        // Head looks forward more intently
        this.parts.head.rotation.x = pitch * 0.3 + aimHeight * 0.15
        
        // Update gun position during shooting
        if (this.parts._permanentGun) {
          this.parts._permanentGun.rotation.x = -0.1 + aimHeight * 0.4 // Gun points forward when shooting
          this.parts._permanentGun.position.z = -0.05 - aimHeight * 0.15 // Gun moves forward
          
          // Add visual feedback - make gun slightly glow when shooting
          this.parts._permanentGun.traverse((child) => {
            if (child.isMesh && child.material) {
              child.material.emissive = new THREE.Color(0x222200) // Slight yellow glow
            }
          })
        }
      }
      
      // Body bobbing (reduced when shooting)
      const bobAmount = this.shooting ? bodyBob * 0.3 : bodyBob
      this.parts.body.position.y = legsH + bodyH / 2 + bobAmount
      
      this.group.rotation.y = yaw
      if (!this.shooting) {
        this.parts.head.rotation.x = pitch * 0.5
      }
      this.lastPos.copy(pos)
    },
    
    startShooting() {
      this.shooting = true
      this.shootTime = 0
      console.log('Police started shooting animation!', this.isPolice)
      
      // Add bright flash effect when shooting starts
      if (this.isPolice && this.parts._permanentGun) {
        const flash = new THREE.PointLight(0xffff00, 3.0, 5)
        flash.position.copy(this.group.position)
        flash.position.y += 1.5
        scene.add(flash)
        setTimeout(() => scene.remove(flash), 200)
      }
    }
  }
  return rig
}

function getOrCreatePlayerRig(id) {
  let rig = playerRigs.get(id)
  if (!rig) {
    const palette = { skin: 0xffd7b3, shirt: (id===myId)?0x3da5ff:0x5ca56a, pants: 0x2a4b8d }
    rig = createHumanRig(palette)
    scene.add(rig.group)
    playerRigs.set(id, rig)
  }
  return rig
}

// Back-compat alias: if older code references getOrCreatePlayerMesh, return the rig group
function getOrCreatePlayerMesh(id) {
  return getOrCreatePlayerRig(id).group
}

function getOrCreateNPCRig(id, kind) {
  let rig = npcRigs.get(id)
  if (!rig) {
    const isPolice = kind === 'police'
    const palette = isPolice 
      ? { skin: 0xffd7b3, shirt: 0x1a237e, pants: 0x0d47a1 } // Dark blue police uniform
      : { skin: 0xffd7b3, shirt: 0x4caf50, pants: 0x2e7d32 } // Green civilian clothes
    rig = createHumanRig(palette, isPolice)
    scene.add(rig.group)
    npcRigs.set(id, rig)
    
    // Add permanent gun to police
    if (isPolice) {
      attachPermanentGunToPolice(rig)
    }
  }
  return rig
}

let FAST_MODE = true
let HIGH_GRAPHICS = false
function getBlockMaterial(type) {
  if (blockMaterials.has(type)) return blockMaterials.get(type)
  const size = 32 // Increased texture resolution
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  
  // Enhanced type palette with more realistic colors
  let baseColor = '#888'
  let roughness = 0.9
  let metalness = 0.0
  let normalStrength = 0.0
  
  if (type === 1) { // stone
    baseColor = '#8a8a8a'
    roughness = 0.8
    metalness = 0.1
    normalStrength = 0.3
  } else if (type === 2) { // wood trunk
    baseColor = '#8b5a2b'
    roughness = 0.7
    metalness = 0.0
    normalStrength = 0.4
  } else if (type === 3) { // leaves
    baseColor = '#4a7c59'
    roughness = 0.9
    metalness = 0.0
    normalStrength = 0.2
  } else if (type === 4) { // dirt
    baseColor = '#8b4513'
    roughness = 0.9
    metalness = 0.0
    normalStrength = 0.1
  } else if (type === 5) { // sand
    baseColor = '#f4e4bc'
    roughness = 0.8
    metalness = 0.0
    normalStrength = 0.1
  } else if (type === 6) { // water
    baseColor = '#4a90e2'
    roughness = 0.1
    metalness = 0.0
    normalStrength = 0.0
  } else if (type === 7) { // road
    baseColor = '#4a4a4a'
    roughness = 0.6
    metalness = 0.0
    normalStrength = 0.2
  } else if (type === 8) { // glass
    baseColor = '#b8d4f0'
    roughness = 0.0
    metalness = 0.0
    normalStrength = 0.0
  } else if (type === 9) { // roof
    baseColor = '#8b4513'
    roughness = 0.7
    metalness = 0.0
    normalStrength = 0.3
  }
  
  // Create more detailed texture with patterns
  ctx.fillStyle = baseColor
  ctx.fillRect(0, 0, size, size)
  
  // Add texture patterns based on block type
  if (type === 1) { // Stone - add cracks and variations
    ctx.fillStyle = '#6a6a6a'
    for (let i = 0; i < 8; i++) {
      const x = Math.random() * size
      const y = Math.random() * size
      const w = Math.random() * 3 + 1
      const h = Math.random() * 3 + 1
      ctx.fillRect(x, y, w, h)
    }
  } else if (type === 2) { // Wood - add grain
    ctx.strokeStyle = '#6b4423'
    ctx.lineWidth = 1
    for (let i = 0; i < 6; i++) {
      const y = (i + 1) * size / 7
      ctx.beginPath()
      ctx.moveTo(0, y)
      ctx.lineTo(size, y + Math.sin(i) * 2)
      ctx.stroke()
    }
  } else if (type === 3) { // Leaves - add leaf patterns
    ctx.fillStyle = '#3d5a47'
    for (let i = 0; i < 12; i++) {
      const x = Math.random() * size
      const y = Math.random() * size
      const r = Math.random() * 3 + 1
      ctx.beginPath()
      ctx.arc(x, y, r, 0, Math.PI * 2)
      ctx.fill()
    }
  } else if (type === 4) { // Dirt - add clumps
    ctx.fillStyle = '#654321'
    for (let i = 0; i < 15; i++) {
      const x = Math.random() * size
      const y = Math.random() * size
      const r = Math.random() * 2 + 0.5
      ctx.beginPath()
      ctx.arc(x, y, r, 0, Math.PI * 2)
      ctx.fill()
    }
  }
  
  // Add subtle noise for all textures
  const img = ctx.getImageData(0, 0, size, size)
  const data = img.data
  for (let i = 0; i < data.length; i += 4) {
    const n = (Math.random() - 0.5) * 15
    data[i] = Math.max(0, Math.min(255, data[i] + n))
    data[i+1] = Math.max(0, Math.min(255, data[i+1] + n))
    data[i+2] = Math.max(0, Math.min(255, data[i+2] + n))
  }
  ctx.putImageData(img, 0, 0)
  
  const tex = new THREE.CanvasTexture(canvas)
  tex.magFilter = THREE.LinearFilter
  tex.minFilter = THREE.LinearMipmapLinearFilter
  tex.wrapS = THREE.RepeatWrapping
  tex.wrapT = THREE.RepeatWrapping
  tex.repeat.set(1, 1)
  
  const transparent = (type === 6 || type === 8)
  const opacity = (type === 6) ? 0.7 : (type === 8 ? 0.4 : 1.0)
  
  const mat = FAST_MODE
    ? new THREE.MeshBasicMaterial({ map: tex, transparent, opacity })
    : new THREE.MeshStandardMaterial({ 
        map: tex, 
        transparent, 
        opacity,
        roughness: roughness,
        metalness: metalness,
        normalScale: new THREE.Vector2(normalStrength, normalStrength)
      })
  
  blockMaterials.set(type, mat)
  return mat
}

function setGraphicsMode(high) {
  HIGH_GRAPHICS = !!high
  FAST_MODE = !HIGH_GRAPHICS
  
  // Enhanced lighting for high graphics
  ambient.intensity = HIGH_GRAPHICS ? 0.4 : 0.3
  sun.intensity = HIGH_GRAPHICS ? 1.2 : 0.8
  fillLight.intensity = HIGH_GRAPHICS ? 0.3 : 0.2
  rimLight.intensity = HIGH_GRAPHICS ? 0.25 : 0.15
  
  // Enhanced rendering settings
  renderer.toneMapping = HIGH_GRAPHICS ? THREE.ACESFilmicToneMapping : THREE.NoToneMapping
  renderer.toneMappingExposure = HIGH_GRAPHICS ? 1.2 : 1.0
  renderer.shadowMap.enabled = HIGH_GRAPHICS
  renderer.shadowMap.type = HIGH_GRAPHICS ? THREE.PCFSoftShadowMap : THREE.BasicShadowMap
  renderer.shadowMap.autoUpdate = HIGH_GRAPHICS
  
  // Enable shadows for all lights
  sun.castShadow = HIGH_GRAPHICS
  fillLight.castShadow = HIGH_GRAPHICS
  rimLight.castShadow = HIGH_GRAPHICS
  
  // Ground receives shadows
  ground.receiveShadow = HIGH_GRAPHICS
  
  // Enhanced shadow settings
  if (HIGH_GRAPHICS) {
    const s = sun.shadow
    s.mapSize.set(2048, 2048)
    s.camera.near = 0.5
    s.camera.far = 200
    s.camera.left = -50
    s.camera.right = 50
    s.camera.top = 50
    s.camera.bottom = -50
    s.bias = -0.0001
  }
  
  // Atmospheric fog
  scene.fog = HIGH_GRAPHICS ? new THREE.FogExp2(0x87ceeb, 0.002) : null
  
  // Enable shadows for all existing objects
  scene.traverse((child) => {
    if (child.isMesh) {
      child.castShadow = HIGH_GRAPHICS
      child.receiveShadow = HIGH_GRAPHICS
    }
  })
  
  // Rebuild materials and instanced meshes
  for (const [type, mesh] of instancedByType) { scene.remove(mesh) }
  instancedByType.clear()
  instanceKeysByType.clear()
  blockMaterials.clear()
  updateVisibleBlocks(true)
}

function createBlockMesh(x, y, z, type = 1) {
  // reuse shared box geometry to reduce allocations
  window.__unitBoxGeom = window.__unitBoxGeom || new THREE.BoxGeometry(1, 1, 1)
  const geom = window.__unitBoxGeom
  const mat = getBlockMaterial(type)
  const mesh = new THREE.Mesh(geom, mat)
  mesh.position.set(x + 0.5, y + 0.5, z + 0.5)
  mesh.userData.cell = { x, y, z, type }
  mesh.matrixAutoUpdate = false
  mesh.updateMatrix()
  scene.add(mesh)
  return mesh
}

function addBlock(x, y, z, type = 1) {
  const key = blockKey(x, y, z)
  allBlocks.set(key, type)
  if (blockMeshes.has(key)) return
  if (isWithinRadius(x, z)) {
    const mesh = createBlockMesh(x, y, z, type)
    blockMeshes.set(key, mesh)
  }
}

function removeBlock(x, y, z) {
  const key = blockKey(x, y, z)
  allBlocks.delete(key)
  const mesh = blockMeshes.get(key)
  if (!mesh) return
  scene.remove(mesh)
  mesh.geometry.dispose()
  if (Array.isArray(mesh.material)) mesh.material.forEach(m => m.dispose())
  else mesh.material.dispose()
  blockMeshes.delete(key)
}

// Visibility management for lazy block meshes
let lastVisUpdate = 0
let lastVisX = Infinity, lastVisZ = Infinity
function updateVisibleBlocks(force = false) {
  const now = performance.now()
  const cx = camera.position.x || 0, cz = camera.position.z || 0
  const moved = Math.hypot((cx - lastVisX), (cz - lastVisZ)) > 2
  if (!force && !moved && (now - lastVisUpdate) < 200) return
  lastVisUpdate = now; lastVisX = cx; lastVisZ = cz
  // gather visible keys by type
  const typeToList = new Map()
  for (const [key, type] of allBlocks) {
    const [sx, sy, sz] = key.split(',').map(Number)
    if (!isWithinRadius(sx, sz)) continue
    let arr = typeToList.get(type)
    if (!arr) { arr = []; typeToList.set(type, arr) }
    arr.push([sx, sy, sz, key])
  }
  // remove types no longer visible
  for (const [type, mesh] of instancedByType) {
    if (!typeToList.has(type)) {
      scene.remove(mesh)
      instancedByType.delete(type)
      instanceKeysByType.delete(type)
    }
  }
  // create/update meshes per type
  for (const [type, list] of typeToList) {
    const count = list.length
    const mat = getBlockMaterial(type)
    let mesh = instancedByType.get(type)
    if (!mesh || mesh.count !== count) {
      if (mesh) scene.remove(mesh)
      window.__unitBoxGeom = window.__unitBoxGeom || new THREE.BoxGeometry(1, 1, 1)
      mesh = new THREE.InstancedMesh(window.__unitBoxGeom, mat, count)
      mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage)
      mesh.userData.type = type
      if (type === 6 || type === 8) {
        mesh.material.transparent = true; mesh.material.depthWrite = false; mesh.renderOrder = 10
      } else {
        mesh.renderOrder = 0
      }
      instancedByType.set(type, mesh)
      scene.add(mesh)
    }
    const keysArray = []
    let minX=Infinity,minY=Infinity,minZ=Infinity,maxX=-Infinity,maxY=-Infinity,maxZ=-Infinity
    for (let i = 0; i < count; i++) {
      const [x, y, z, key] = list[i]
      tmpMatrix.makeTranslation(x + 0.5, y + 0.5, z + 0.5)
      mesh.setMatrixAt(i, tmpMatrix)
      keysArray.push(key)
      if (x<minX) minX=x; if (y<minY) minY=y; if (z<minZ) minZ=z
      if (x>maxX) maxX=x; if (y>maxY) maxY=y; if (z>maxZ) maxZ=z
    }
    mesh.instanceMatrix.needsUpdate = true
    instanceKeysByType.set(type, keysArray)
    // approximate bounds for frustum culling
    const cx = (minX + maxX) * 0.5 + 0.5
    const cy = (minY + maxY) * 0.5 + 0.5
    const cz = (minZ + maxZ) * 0.5 + 0.5
    const dx = (maxX - minX + 1) * 0.5
    const dy = (maxY - minY + 1) * 0.5
    const dz = (maxZ - minZ + 1) * 0.5
    const radius = Math.sqrt(dx*dx + dy*dy + dz*dz)
    mesh.userData.sphere = new THREE.Sphere(new THREE.Vector3(cx, cy, cz), radius)
  }
}

// Input handling (pointer lock + WASD)
let pointerLocked = false
let yaw = 0, pitch = 0
const pressed = new Set()
let wantJump = false
let sprint = false

window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight
  camera.updateProjectionMatrix()
  renderer.setSize(window.innerWidth, window.innerHeight)
})

document.addEventListener('keydown', (e) => {
  pressed.add(e.code)
  if (e.code === 'Space') wantJump = true
  if (e.code === 'ShiftLeft' || e.code === 'ShiftRight') sprint = true
  if (e.code.startsWith('Digit')) {
    const num = parseInt(e.code.slice(5), 10)
    if (num >= 1 && num <= 5) { selectedKind='block'; setSelectedType(num) }
    if (num === 6) { setSelectedGun('pistol') }
  }
})

document.addEventListener('keyup', (e) => {
  pressed.delete(e.code)
  if (e.code === 'ShiftLeft' || e.code === 'ShiftRight') sprint = false
})

document.addEventListener('click', () => {
  if (!pointerLocked) renderer.domElement.requestPointerLock()
})

document.addEventListener('pointerlockchange', () => {
  pointerLocked = document.pointerLockElement === renderer.domElement
  overlay.classList.toggle('hidden', pointerLocked)
  if (hotbar) hotbar.style.opacity = pointerLocked ? '1' : '0.5'
})

// UI buttons in overlay
const btnView = document.getElementById('btnView')
const btnGraphics = document.getElementById('btnGraphics')
if (btnView) btnView.onclick = (e) => {
  e.stopPropagation()
  thirdPerson = !thirdPerson
  fpGroup.visible = !thirdPerson && selectedKind === 'gun'
  btnView.textContent = `View: ${thirdPerson ? 'Third' : 'First'}`
}
if (btnGraphics) btnGraphics.onclick = (e) => {
  e.stopPropagation()
  setGraphicsMode(!HIGH_GRAPHICS)
  btnGraphics.textContent = `Graphics: ${HIGH_GRAPHICS ? 'High' : 'Low'}`
}

document.addEventListener('mousemove', (e) => {
  if (!pointerLocked) return
  const sensitivity = 0.002
  yaw -= e.movementX * sensitivity
  pitch -= e.movementY * sensitivity
  pitch = Math.max(-Math.PI / 2 + 0.01, Math.min(Math.PI / 2 - 0.01, pitch))
})

function computeInputAxes() {
  // Build movement basis directly from camera orientation to match visuals
  camera.rotation.set(pitch, yaw, 0, 'YXZ')
  camera.updateMatrixWorld(true)

  const forward = new THREE.Vector3()
  camera.getWorldDirection(forward)
  forward.y = 0
  if (forward.lengthSq() === 0) forward.set(0, 0, -1)
  forward.normalize()

  const right = new THREE.Vector3().crossVectors(forward, new THREE.Vector3(0, 1, 0)).normalize()
  // left = -right

  let move = new THREE.Vector3(0, 0, 0)
  if (pressed.has('KeyW')) move.add(forward)
  if (pressed.has('KeyS')) move.sub(forward)
  if (pressed.has('KeyD')) move.add(right)
  if (pressed.has('KeyA')) move.sub(right)
  // normalize to unit length
  if (move.lengthSq() > 0) move.normalize()
  // third-person free mode vertical movement (R/F handled in keydown for camera; use Space/Shift for fly)
  let fy = 0
  if (thirdPerson) {
    if (pressed.has('Space')) fy += 1
    if (pressed.has('ShiftLeft') || pressed.has('ShiftRight')) fy -= 1
  }
  return { ax: move.x, az: move.z, fy }
}

// WebSocket setup
let socket = null
function connect() {
  socket = new WebSocket(`ws://${location.hostname}:8080/ws`)
  socket.addEventListener('open', () => {
    console.log('connected')
  })
  socket.addEventListener('message', (ev) => {
    const msg = JSON.parse(ev.data)
    if (msg.type === 'hello') {
      myId = msg.id
      if (Array.isArray(msg.blocks)) {
        for (const b of msg.blocks) addBlock(b.x, b.y, b.z, b.type)
      }
      updateVisibleBlocks(true)
    } else if (msg.type === 'state') {
      handleState(msg)
    } else if (msg.type === 'blockUpdate') {
      if (msg.action === 'set') addBlock(msg.x, msg.y, msg.z, msg.block)
      else if (msg.action === 'remove') removeBlock(msg.x, msg.y, msg.z)
    } else if (msg.type === 'shot') {
      const now = performance.now()
      if (msg.ownerId) {
        lastShotById.set(msg.ownerId, now)
      } else if (msg.ownerKind === 'npc') {
        // Find the shooting NPC and trigger shooting animation
        const sx = msg.sx, sy = msg.sy, sz = msg.sz
        let bestId = null, bestD = Infinity
        for (const [id, rig] of npcRigs) {
          const p = rig.group.position
          const d = Math.hypot(p.x - sx, p.y - sy, p.z - sz)
          if (d < bestD) { bestD = d; bestId = id }
        }
        
        if (bestId && bestD < 2.0) {
          lastShotById.set(bestId, now)
          const shootingRig = npcRigs.get(bestId)
          if (shootingRig && shootingRig.startShooting) {
            shootingRig.startShooting()
          }
          
          // Add muzzle flash for police
          if (shootingRig && shootingRig.isPolice) {
            const light = new THREE.PointLight(0xffeeaa, 2.0, 3)
            light.position.set(sx, sy, sz)
            scene.add(light)
            setTimeout(() => scene.remove(light), 100)
          }
        }
      }
    } else if (msg.type === 'death') {
      if (msg.playerId === myId) {
        // pause and show overlay
        document.exitPointerLock?.()
        deathOverlay.style.display = 'grid'
      }
    }
  })
  socket.addEventListener('close', () => {
    setTimeout(connect, 1000)
  })
}
connect()

function handleState(msg) {
  if (!myId) return
  const players = msg.players
  for (const p of players) {
    const rig = getOrCreatePlayerRig(p.id)
    rig.group.position.set(p.x, p.y, p.z)
    const now = performance.now()
    rig._lastTime = rig._lastTime ?? now
    const dt = Math.min(0.05, (now - rig._lastTime) / 1000)
    rig._lastTime = now
    const yawVal = (p.yaw ?? 0)
    const pitchVal = (p.pitch ?? 0)
    rig.update(new THREE.Vector3(p.x, p.y, p.z), yawVal, pitchVal, dt)
    const tShot = lastShotById.get(p.id)
    if (tShot && now - tShot < 120) {
      if (rig.isPolice && rig.parts._permanentGun) {
        // Police already have permanent guns, just trigger shooting animation
        if (!rig.shooting) {
          rig.startShooting()
        }
      } else {
        // Non-police players get temporary guns
        attachGunToRightArm(rig)
      }
    }
    // show local body in third-person only
    rig.group.visible = !(p.id === myId && !thirdPerson)
    if (p.id === myId) {
      const eyeHeight = 1.6
      if (!thirdPerson) {
        camera.position.set(p.x, Math.max(p.y + eyeHeight, 0.2), p.z)
      } else {
        // third-person: smooth follow with damping
        const back = new THREE.Vector3(Math.sin(yaw), 0, Math.cos(yaw))
        const target = new THREE.Vector3(p.x, Math.max(p.y + eyeHeight, 0.2), p.z)
        target.addScaledVector(back.negate(), thirdOffset.z)
        target.y += thirdOffset.y
        camera.position.lerp(target, 0.18)
        camera.lookAt(p.x, p.y + eyeHeight * 0.9, p.z)
      }
      camera.rotation.set(pitch, yaw, 0, 'YXZ')
    }
  }
  // NPCs
  if (Array.isArray(msg.npcs)) {
    const seen = new Set()
    for (const n of msg.npcs) {
      const rig = getOrCreateNPCRig(n.id, n.kind)
      rig.group.position.set(n.x, n.y, n.z)
      const now2 = performance.now()
      rig._lastTime = rig._lastTime ?? now2
      const dt2 = Math.min(0.05, (now2 - rig._lastTime) / 1000)
      rig._lastTime = now2
      // make NPCs face their movement/camera direction more strongly
      const dirYaw = Math.atan2(-(n.x - (rig.lastPos?.x ?? n.x)), -(n.z - (rig.lastPos?.z ?? n.z))) || rig.group.rotation.y
      rig.update(new THREE.Vector3(n.x, n.y, n.z), dirYaw, 0, dt2)
      // show gun if recently shot and trigger shooting animation
      const t = lastShotById.get(n.id)
      if (t && now2 - t < 200) { // Increased detection window
        if (rig.isPolice && rig.parts._permanentGun) {
          // Police already have permanent guns, just trigger shooting animation
          if (!rig.shooting) {
            rig.startShooting()
          }
        } else {
          // Non-police NPCs get temporary guns
          attachGunToRightArm(rig)
        }
      }
      
      // Test: Make police shoot periodically for demonstration
      if (rig.isPolice && rig.parts._permanentGun && !rig.shooting) {
        // Random chance to shoot every few seconds - increased probability
        if (Math.random() < 0.05) { // 5% chance per frame for testing
          rig.startShooting()
          console.log('Police test shooting triggered!', n.id)
        }
        
        // Also shoot when player is nearby
        if (myId) {
          const playerRig = playerRigs.get(myId)
          if (playerRig) {
            const distance = Math.hypot(
              rig.group.position.x - playerRig.group.position.x,
              rig.group.position.z - playerRig.group.position.z
            )
            if (distance < 10 && Math.random() < 0.1) { // 10% chance when close
              rig.startShooting()
              console.log('Police shooting at nearby player!', n.id, 'distance:', distance)
            }
          }
        }
      }
      seen.add(n.id)
    }
    // remove stale
    for (const [id, rig] of npcRigs) {
      if (!seen.has(id)) {
        scene.remove(rig.group)
        npcRigs.delete(id)
      }
    }
  }
  // update HP bar from local player data if available
  const me = players.find(p => p.id === myId)
  if (me) {
    const pct = Math.max(0, Math.min(1, (me.hp ?? 100) / 100))
    hpBar.style.width = `${pct * 100}%`
  }
}

// Send inputs at 30 Hz
setInterval(() => {
  if (!socket || socket.readyState !== WebSocket.OPEN) return
  if (!pointerLocked) return
  const { ax, az, fy } = computeInputAxes()
  const jump = wantJump; wantJump = false
  socket.send(JSON.stringify({ type: 'input', input: { ax, az, fy, jump, sprint, yaw, pitch, third: thirdPerson } }))
}, 33)

// Build: place/remove blocks with mouse
window.addEventListener('contextmenu', (e) => e.preventDefault())
window.addEventListener('mousedown', (e) => {
  if (!pointerLocked) return
  // ray from center
  camera.rotation.set(pitch, yaw, 0, 'YXZ')
  camera.updateMatrixWorld(true)
  raycaster.setFromCamera({ x: 0, y: 0 }, camera)
  // Ensure latest camera rotation is used
  // (matrix updated above)
  const blockList = Array.from(blockMeshes.values())
  const hits = raycaster.intersectObjects(blockList, false)
  // also check ground plane for placement
  const p = new THREE.Vector3()
  if (e.button === 2) { // right: remove block if hit
    if (hits.length) {
      const hit = hits[0]
      const cell = hit.object.userData.cell
      socket?.send(JSON.stringify({ type: 'removeBlock', x: cell.x, y: cell.y, z: cell.z }))
    }
    return
  }
  if (e.button === 0) {
    if (selectedKind==='gun') {
      const dir = new THREE.Vector3()
      camera.getWorldDirection(dir)
      socket?.send(JSON.stringify({ type: 'shoot', dir: { x: dir.x, y: dir.y, z: dir.z } }))
      muzzleFlash(); playGunshot(); fpRecoil = Math.min(0.25, fpRecoil + 0.12); fpGroup.visible = true
      return
    }
    // place block
    if (hits.length) {
      const hit = hits[0]
      const cell = hit.object.userData.cell
      // place adjacent along face normal
      const n = (hit.face && hit.face.normal ? hit.face.normal.clone() : new THREE.Vector3(0, 1, 0))
      const nx = Math.round(n.x), ny = Math.round(n.y), nz = Math.round(n.z)
      const x = cell.x + nx
      const y = cell.y + ny
      const z = cell.z + nz
      const key = blockKey(x, y, z)
      if (!blockMeshes.has(key)) {
        socket?.send(JSON.stringify({ type: 'placeBlock', x, y, z, block: selectedType }))
      }
    } else {
      // place on ground where ray hits y=0
      if (raycaster.ray.intersectPlane(groundPlane, p)) {
        const x = Math.floor(p.x)
        const z = Math.floor(p.z)
        const y = 0
        const key = blockKey(x, y, z)
        if (!blockMeshes.has(key)) {
          socket?.send(JSON.stringify({ type: 'placeBlock', x, y, z, block: selectedType }))
        }
      }
    }
  }
})

// Respawn on death overlay click
deathOverlay.addEventListener('click', () => {
  if (!socket || socket.readyState !== WebSocket.OPEN) return
  socket.send(JSON.stringify({ type: 'respawn' }))
  deathOverlay.style.display = 'none'
})

function muzzleFlash() {
  const flare = new THREE.PointLight(0xffeeaa, 2, 4)
  flare.position.copy(camera.position)
  scene.add(flare)
  setTimeout(() => scene.remove(flare), 60)
}

function attachGunToRightArm(rig) {
  if (!rig || !rig.parts || !rig.parts.rightArm) return
  if (!rig.parts._gun) {
    const gunGeom = new THREE.BoxGeometry(0.12, 0.06, 0.25)
    const gunMat = new THREE.MeshLambertMaterial({ color: 0x333333 })
    const gun = new THREE.Mesh(gunGeom, gunMat)
    gun.position.set(0.1, -0.2, -0.1)
    rig.parts.rightArm.add(gun)
    rig.parts._gun = gun
  }
  // muzzle flash near gun tip
  const light = new THREE.PointLight(0xffeeaa, 1.5, 2)
  light.position.set(0.1, -0.2, -0.22)
  rig.parts.rightArm.add(light)
  setTimeout(() => rig.parts.rightArm.remove(light), 60)
}

function attachPermanentGunToPolice(rig) {
  if (!rig || !rig.parts || !rig.parts.rightArm) return
  
  // Create a more detailed gun model
  const gunGroup = new THREE.Group()
  
  // Main gun body
  const gunBodyGeom = new THREE.BoxGeometry(0.08, 0.06, 0.3)
  const gunBodyMat = new THREE.MeshStandardMaterial({ 
    color: 0x2a2a2a, 
    roughness: 0.3, 
    metalness: 0.8 
  })
  const gunBody = new THREE.Mesh(gunBodyGeom, gunBodyMat)
  gunBody.position.set(0.05, -0.15, -0.1)
  gunGroup.add(gunBody)
  
  // Gun barrel
  const barrelGeom = new THREE.CylinderGeometry(0.02, 0.02, 0.2, 8)
  const barrelMat = new THREE.MeshStandardMaterial({ 
    color: 0x1a1a1a, 
    roughness: 0.2, 
    metalness: 0.9 
  })
  const barrel = new THREE.Mesh(barrelGeom, barrelMat)
  barrel.rotation.z = Math.PI / 2
  barrel.position.set(0.05, -0.15, -0.25)
  gunGroup.add(barrel)
  
  // Gun handle
  const handleGeom = new THREE.BoxGeometry(0.06, 0.12, 0.08)
  const handleMat = new THREE.MeshStandardMaterial({ 
    color: 0x8b4513, 
    roughness: 0.8, 
    metalness: 0.0 
  })
  const handle = new THREE.Mesh(handleGeom, handleMat)
  handle.position.set(0.05, -0.25, -0.05)
  gunGroup.add(handle)
  
  // Trigger guard
  const guardGeom = new THREE.TorusGeometry(0.04, 0.01, 4, 8, Math.PI)
  const guardMat = new THREE.MeshStandardMaterial({ 
    color: 0x2a2a2a, 
    roughness: 0.3, 
    metalness: 0.8 
  })
  const guard = new THREE.Mesh(guardGeom, guardMat)
  guard.rotation.x = Math.PI / 2
  guard.position.set(0.05, -0.2, -0.05)
  gunGroup.add(guard)
  
  // Position gun for proper holding (resting position)
  gunGroup.position.set(0.15, -0.15, -0.05) // More visible position
  gunGroup.rotation.x = 0.1 // Slight downward angle when not shooting
  gunGroup.rotation.z = 0.1 // Slight side angle for better visibility
  
  // Attach to right arm
  rig.parts.rightArm.add(gunGroup)
  rig.parts._permanentGun = gunGroup
  
  // Make gun cast shadows
  gunGroup.traverse((child) => {
    if (child.isMesh) {
      child.castShadow = true
    }
  })
}

function setSelectedType(type) {
  selectedType = type
  document.querySelectorAll('#hotbar .slot').forEach(el => {
    const t = parseInt(el.getAttribute('data-type'), 10)
    if (el.getAttribute('data-kind')==='block') {
      el.style.borderColor = (selectedKind==='block' && t === selectedType) ? '#fff' : '#fff3'
      el.style.background = (selectedKind==='block' && t === selectedType) ? '#444a' : '#222a'
    }
  })
  crosshair.style.opacity = '0'
  fpGroup.visible = false
}

document.querySelectorAll('#hotbar .slot').forEach(el => {
  el.addEventListener('click', (e) => {
    e.stopPropagation()
    const kind = el.getAttribute('data-kind')
    if (kind==='block') { selectedKind='block'; const t = parseInt(el.getAttribute('data-type'), 10); setSelectedType(t) }
    if (kind==='gun') { setSelectedGun(el.getAttribute('data-gun')) }
  })
})

window.addEventListener('wheel', (e) => {
  if (!pointerLocked) return
  if (selectedKind==='block') {
    if (e.deltaY > 0) setSelectedType(((selectedType - 1 + 1) % 5) + 1)
    else if (e.deltaY < 0) setSelectedType(((selectedType - 1 - 1 + 5) % 5) + 1)
  }
})

// Toggle first/third person
window.addEventListener('keydown', (e) => {
  if (e.code === 'KeyV') {
    thirdPerson = !thirdPerson
    fpGroup.visible = !thirdPerson && selectedKind === 'gun'
  } else if (e.code === 'KeyG') {
    setGraphicsMode(!HIGH_GRAPHICS)
  } else if (thirdPerson && (e.code === 'KeyR' || e.code === 'KeyF' || e.code === 'BracketRight' || e.code === 'BracketLeft')) {
    // Third-person camera vertical (R/F) and distance ([/]) adjustments
    const yStep = 0.3
    const zStep = 0.3
    if (e.code === 'KeyR') thirdOffset.y = Math.min(6.0, thirdOffset.y + yStep)
    if (e.code === 'KeyF') thirdOffset.y = Math.max(0.2, thirdOffset.y - yStep)
    if (e.code === 'BracketRight') thirdOffset.z = Math.min(8.0, thirdOffset.z + zStep)
    if (e.code === 'BracketLeft') thirdOffset.z = Math.max(1.2, thirdOffset.z - zStep)
  }
})

setSelectedType(1)

function setSelectedGun(name) {
  selectedKind = 'gun'
  document.querySelectorAll('#hotbar .slot').forEach(el => {
    if (el.getAttribute('data-kind')==='gun') { el.style.borderColor = '#fff'; el.style.background = '#444a' }
    else { el.style.borderColor = '#fff3'; el.style.background = '#222a' }
  })
  crosshair.style.opacity = '1'
  fpGroup.visible = true
}

// gunshot audio via WebAudio
function playGunshot() {
  const ctx = new (window.AudioContext || window.webkitAudioContext)()
  const o = ctx.createOscillator()
  const g = ctx.createGain()
  o.type = 'square'
  o.frequency.setValueAtTime(800, ctx.currentTime)
  o.frequency.exponentialRampToValueAtTime(120, ctx.currentTime + 0.1)
  g.gain.setValueAtTime(0.4, ctx.currentTime)
  g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.15)
  o.connect(g).connect(ctx.destination)
  o.start()
  o.stop(ctx.currentTime + 0.16)
}

// Render loop with dynamic resolution scaling (DRS)
let lastTime = performance.now()
let emaFrame = 16.7 // ms, exponential moving average
let lastDprAdjust = 0
function animate(now = performance.now()) {
  requestAnimationFrame(animate)
  const dt = now - lastTime
  lastTime = now
  // recoil decay
  fpRecoil *= 0.85
  fpGroup.position.set(0.35, -0.35, -0.8 - fpRecoil)
  // update visible blocks (throttled inside)
  updateVisibleBlocks()
  // frustum culling for instanced groups
  camera.updateMatrixWorld()
  projScreenMatrix.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse)
  frustum.setFromProjectionMatrix(projScreenMatrix)
  for (const mesh of instancedByType.values()) {
    const sph = mesh.userData.sphere
    mesh.visible = !sph || frustum.intersectsSphere(sph)
  }
  // DRS: adjust pixel ratio gradually to target smoothness (~16-20ms)
  emaFrame = emaFrame * 0.9 + dt * 0.1
  if (now - lastDprAdjust > 500) {
    lastDprAdjust = now
    if (emaFrame > 22 && currentDPR > 0.6) {
      currentDPR = Math.max(0.6, currentDPR - 0.1)
      renderer.setPixelRatio(currentDPR)
      renderer.setSize(window.innerWidth, window.innerHeight, false)
    } else if (emaFrame < 14 && currentDPR < Math.min(window.devicePixelRatio || 1, 1.25)) {
      currentDPR = Math.min(Math.min(window.devicePixelRatio || 1, 1.25), currentDPR + 0.05)
      renderer.setPixelRatio(currentDPR)
      renderer.setSize(window.innerWidth, window.innerHeight, false)
    }
  }
  renderer.render(scene, camera)
}
animate()
