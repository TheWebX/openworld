import * as THREE from 'three'

const app = document.getElementById('app')
const overlay = document.getElementById('overlay')

// Three.js setup
const renderer = new THREE.WebGLRenderer({ antialias: true })
renderer.setPixelRatio(Math.min(devicePixelRatio, 2))
renderer.setSize(window.innerWidth, window.innerHeight)
app.appendChild(renderer.domElement)

const scene = new THREE.Scene()
scene.background = new THREE.Color('#87ceeb')

const camera = new THREE.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.1, 1000)
scene.add(camera)

const ambient = new THREE.AmbientLight(0xffffff, 0.6)
scene.add(ambient)
const sun = new THREE.DirectionalLight(0xffffff, 0.7)
sun.position.set(0.5, 1, 0.3)
scene.add(sun)

// Ground plane (grass style)
const groundGeom = new THREE.PlaneGeometry(2000, 2000)
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
  tex.minFilter = THREE.LinearMipmapLinearFilter
  tex.repeat.set(512, 512)
  return tex
}
const groundMat = new THREE.MeshLambertMaterial({ map: createGrassTexture() })
const ground = new THREE.Mesh(groundGeom, groundMat)
ground.receiveShadow = false
scene.add(ground)

// Player visuals and blocks
const playerMeshes = new Map()
let myId = null
const blockMeshes = new Map() // key: "x,y,z" -> mesh
const raycaster = new THREE.Raycaster()
const groundPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0)

function blockKey(x, y, z) { return `${x},${y},${z}` }

function getOrCreatePlayerMesh(id) {
  let mesh = playerMeshes.get(id)
  if (!mesh) {
    const geom = new THREE.BoxGeometry(0.6, 1.8, 0.6)
    const mat = new THREE.MeshLambertMaterial({ color: id === myId ? 0x33aaff : 0xffcc66 })
    mesh = new THREE.Mesh(geom, mat)
    mesh.castShadow = false
    mesh.receiveShadow = false
    scene.add(mesh)
    playerMeshes.set(id, mesh)
  }
  return mesh
}

function createBlockMesh(x, y, z, type = 1) {
  const geom = new THREE.BoxGeometry(1, 1, 1)
  const mat = new THREE.MeshLambertMaterial({ color: 0x8fbf6d })
  const mesh = new THREE.Mesh(geom, mat)
  mesh.position.set(x + 0.5, y + 0.5, z + 0.5)
  mesh.userData.cell = { x, y, z, type }
  scene.add(mesh)
  return mesh
}

function addBlock(x, y, z, type = 1) {
  const key = blockKey(x, y, z)
  if (blockMeshes.has(key)) return
  const mesh = createBlockMesh(x, y, z, type)
  blockMeshes.set(key, mesh)
}

function removeBlock(x, y, z) {
  const key = blockKey(x, y, z)
  const mesh = blockMeshes.get(key)
  if (!mesh) return
  scene.remove(mesh)
  mesh.geometry.dispose()
  if (Array.isArray(mesh.material)) mesh.material.forEach(m => m.dispose())
  else mesh.material.dispose()
  blockMeshes.delete(key)
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
    } else if (msg.type === 'state') {
      handleState(msg)
    } else if (msg.type === 'blockUpdate') {
      if (msg.action === 'set') addBlock(msg.x, msg.y, msg.z, msg.block)
      else if (msg.action === 'remove') removeBlock(msg.x, msg.y, msg.z)
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
    const mesh = getOrCreatePlayerMesh(p.id)
    mesh.position.set(p.x, p.y, p.z)
    if (p.id === myId) {
      const eyeHeight = 1.6
      camera.position.set(p.x, Math.max(p.y + eyeHeight, 0.2), p.z)
      camera.rotation.set(pitch, yaw, 0, 'YXZ')
    }
  }
}

// Send inputs at 30 Hz
setInterval(() => {
  if (!socket || socket.readyState !== WebSocket.OPEN) return
  if (!pointerLocked) return
  const { ax, az } = computeInputAxes()
  const jump = wantJump; wantJump = false
  socket.send(JSON.stringify({ type: 'input', input: { ax, az, jump, sprint } }))
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
  if (e.button === 0) { // left: place block
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
        socket?.send(JSON.stringify({ type: 'placeBlock', x, y, z, type: 1 }))
      }
    } else {
      // place on ground where ray hits y=0
      if (raycaster.ray.intersectPlane(groundPlane, p)) {
        const x = Math.floor(p.x)
        const z = Math.floor(p.z)
        const y = 0
        const key = blockKey(x, y, z)
        if (!blockMeshes.has(key)) {
          socket?.send(JSON.stringify({ type: 'placeBlock', x, y, z, type: 1 }))
        }
      }
    }
  }
})

// Render loop
function animate() {
  requestAnimationFrame(animate)
  renderer.render(scene, camera)
}
animate()
