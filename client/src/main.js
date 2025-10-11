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
// First-person hands + gun group (attached to camera)
const fpGroup = new THREE.Group()
fpGroup.position.set(0.35, -0.35, -0.8)
const fpArmGeom = new THREE.BoxGeometry(0.12, 0.4, 0.12)
const fpArmMat = new THREE.MeshLambertMaterial({ color: 0xffd7b3 })
const fpRightArm = new THREE.Mesh(fpArmGeom, fpArmMat)
fpRightArm.position.set(0, -0.2, 0)
const fpGunGeom = new THREE.BoxGeometry(0.25, 0.12, 0.5)
const fpGunMat = new THREE.MeshLambertMaterial({ color: 0x333333 })
const fpGun = new THREE.Mesh(fpGunGeom, fpGunMat)
fpGun.position.set(0.05, -0.05, -0.2)
fpGroup.add(fpRightArm)
fpGroup.add(fpGun)
camera.add(fpGroup)
fpGroup.visible = false
let fpRecoil = 0

const ambient = new THREE.AmbientLight(0xffffff, 0.4)
scene.add(ambient)
const sun = new THREE.DirectionalLight(0xffffff, 0.55)
sun.position.set(0.5, 1, 0.3)
scene.add(sun)

// Ground plane (grass style)
const groundGeom = new THREE.PlaneGeometry(1200, 1200, 1, 1)
groundGeom.rotateX(-Math.PI / 2)
function createGrassTexture() {
  const size = 128
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  const img = ctx.createImageData(size, size)
  const data = img.data
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4
      const n = Math.random() * 0.15 - 0.075 // noise
      // base grass color ~ #6aa84f
      let r = 0x6a / 255 + n
      let g = 0xa8 / 255 + n
      let b = 0x4f / 255 + n
      r = Math.min(1, Math.max(0, r))
      g = Math.min(1, Math.max(0, g))
      b = Math.min(1, Math.max(0, b))
      data[i] = Math.floor(r * 255)
      data[i + 1] = Math.floor(g * 255)
      data[i + 2] = Math.floor(b * 255)
      data[i + 3] = 255
    }
  }
  ctx.putImageData(img, 0, 0)
  const tex = new THREE.CanvasTexture(canvas)
  tex.wrapS = THREE.RepeatWrapping
  tex.wrapT = THREE.RepeatWrapping
  tex.magFilter = THREE.NearestFilter
  tex.minFilter = THREE.NearestFilter
  tex.repeat.set(512, 512)
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

function createHumanRig(palette) {
  const group = new THREE.Group()
  const legsH = 0.8, bodyH = 0.8, headH = 0.2
  const legW = 0.22, legD = 0.22
  const bodyW = 0.5, bodyD = 0.28
  const armW = 0.18, armD = 0.18, armH = 0.8
  const skinMat = new THREE.MeshLambertMaterial({ color: palette.skin })
  const shirtMat = new THREE.MeshLambertMaterial({ color: palette.shirt })
  const pantsMat = new THREE.MeshLambertMaterial({ color: palette.pants })
  const legGeom = new THREE.BoxGeometry(legW, legsH, legD)
  const leftLeg = new THREE.Mesh(legGeom, pantsMat)
  const rightLeg = new THREE.Mesh(legGeom, pantsMat)
  const legOffsetX = (legW / 2) + 0.05
  leftLeg.position.set(-legOffsetX, legsH / 2, 0)
  rightLeg.position.set(legOffsetX, legsH / 2, 0)
  const bodyGeom = new THREE.BoxGeometry(bodyW, bodyH, bodyD)
  const body = new THREE.Mesh(bodyGeom, shirtMat)
  body.position.set(0, legsH + bodyH / 2, 0)
  const armGeom = new THREE.BoxGeometry(armW, armH, armD)
  const leftArm = new THREE.Mesh(armGeom, skinMat)
  const rightArm = new THREE.Mesh(armGeom, skinMat)
  const armOffsetX = (bodyW / 2) + (armW / 2)
  const armY = legsH + armH / 2
  leftArm.position.set(-armOffsetX, armY, 0)
  rightArm.position.set(armOffsetX, armY, 0)
  const headGeom = new THREE.BoxGeometry(0.5, headH, 0.5)
  const head = new THREE.Mesh(headGeom, skinMat)
  head.position.set(0, legsH + bodyH + headH / 2, 0)
  group.add(leftLeg, rightLeg, body, leftArm, rightArm, head)
  const rig = { group, parts: { leftLeg, rightLeg, leftArm, rightArm, head, body }, lastPos: new THREE.Vector3(), walkPhase: 0,
    update(pos, yaw, pitch, dt) {
      const dx = pos.x - this.lastPos.x, dz = pos.z - this.lastPos.z
      const speed = Math.min(1.0, Math.hypot(dx, dz) / Math.max(0.0001, dt))
      this.walkPhase += speed * dt * 6.0
      const swing = Math.sin(this.walkPhase) * 0.6 * speed
      this.parts.leftLeg.rotation.x = swing
      this.parts.rightLeg.rotation.x = -swing
      this.parts.leftArm.rotation.x = -swing * 0.8
      this.parts.rightArm.rotation.x = swing * 0.8
      this.group.rotation.y = yaw
      this.parts.head.rotation.x = pitch * 0.5
      this.lastPos.copy(pos)
    } }
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
    const palette = kind==='police' ? { skin:0xffd7b3, shirt:0x2244ff, pants:0x111133 } : { skin:0xffd7b3, shirt:0x88aa55, pants:0x554433 }
    rig = createHumanRig(palette)
    scene.add(rig.group)
    npcRigs.set(id, rig)
  }
  return rig
}

let FAST_MODE = true
let HIGH_GRAPHICS = false
function getBlockMaterial(type) {
  if (blockMaterials.has(type)) return blockMaterials.get(type)
  const size = 16
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#888'
  // type palette
  if (type === 1) ctx.fillStyle = '#777777' // stone
  if (type === 2) ctx.fillStyle = '#8b5a2b' // wood trunk
  if (type === 3) ctx.fillStyle = '#3faa3f' // leaves
  if (type === 4) ctx.fillStyle = '#996633' // dirt
  if (type === 5) ctx.fillStyle = '#c2b280' // sand
  if (type === 6) ctx.fillStyle = '#3aa7ff' // water (blue, transparent)
  if (type === 7) ctx.fillStyle = '#444444' // road
  if (type === 8) ctx.fillStyle = '#88ccee' // glass
  if (type === 9) ctx.fillStyle = '#7a5546' // roof
  ctx.fillRect(0, 0, size, size)
  // add subtle noise
  const img = ctx.getImageData(0, 0, size, size)
  const data = img.data
  for (let i = 0; i < data.length; i += 4) {
    const n = (Math.random() - 0.5) * 20
    data[i] = Math.max(0, Math.min(255, data[i] + n))
    data[i+1] = Math.max(0, Math.min(255, data[i+1] + n))
    data[i+2] = Math.max(0, Math.min(255, data[i+2] + n))
  }
  ctx.putImageData(img, 0, 0)
  const tex = new THREE.CanvasTexture(canvas)
  tex.magFilter = THREE.NearestFilter
  tex.minFilter = THREE.NearestFilter
  const transparent = (type === 6 || type === 8)
  const opacity = (type === 6) ? 0.6 : (type === 8 ? 0.35 : 1.0)
  const mat = FAST_MODE
    ? new THREE.MeshBasicMaterial({ map: tex, transparent, opacity })
    : new THREE.MeshLambertMaterial({ map: tex, transparent, opacity })
  blockMaterials.set(type, mat)
  return mat
}

function setGraphicsMode(high) {
  HIGH_GRAPHICS = !!high
  FAST_MODE = !HIGH_GRAPHICS
  // adjust lighting slightly for high graphics
  ambient.intensity = HIGH_GRAPHICS ? 0.55 : 0.4
  sun.intensity = HIGH_GRAPHICS ? 0.8 : 0.55
  // rebuild materials and instanced meshes
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
      if (type === 6 || type === 8) { mesh.material.transparent = true; mesh.material.depthWrite = false }
      instancedByType.set(type, mesh)
      scene.add(mesh)
    }
    const keysArray = []
    for (let i = 0; i < count; i++) {
      const [x, y, z, key] = list[i]
      tmpMatrix.makeTranslation(x + 0.5, y + 0.5, z + 0.5)
      mesh.setMatrixAt(i, tmpMatrix)
      keysArray.push(key)
    }
    mesh.instanceMatrix.needsUpdate = true
    instanceKeysByType.set(type, keysArray)
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
  return { ax: move.x, az: move.z }
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
        // infer shooter by nearest NPC to shot start
        const sx = msg.sx, sy = msg.sy, sz = msg.sz
        let bestId = null, bestD = Infinity
        for (const [id, rig] of npcRigs) {
          const p = rig.group.position
          const d = Math.hypot(p.x - sx, p.y - sy, p.z - sz)
          if (d < bestD) { bestD = d; bestId = id }
        }
        if (bestId && bestD < 2.0) lastShotById.set(bestId, now)
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
    if (tShot && now - tShot < 120) attachGunToRightArm(rig)
    // show local body in third-person only
    rig.group.visible = !(p.id === myId && !thirdPerson)
    if (p.id === myId) {
      const eyeHeight = 1.6
      if (!thirdPerson) {
        camera.position.set(p.x, Math.max(p.y + eyeHeight, 0.2), p.z)
      } else {
        // third-person: place camera behind and above
        const back = new THREE.Vector3(Math.sin(yaw), 0, Math.cos(yaw))
        const camPos = new THREE.Vector3(p.x, Math.max(p.y + eyeHeight, 0.2), p.z)
        camPos.addScaledVector(back.negate(), thirdOffset.z)
        camPos.y += thirdOffset.y
        camera.position.copy(camPos)
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
      // show gun if recently shot
      const t = lastShotById.get(n.id)
      if (t && now2 - t < 120) attachGunToRightArm(rig)
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
  const { ax, az } = computeInputAxes()
  const jump = wantJump; wantJump = false
  socket.send(JSON.stringify({ type: 'input', input: { ax, az, jump, sprint, yaw, pitch } }))
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
