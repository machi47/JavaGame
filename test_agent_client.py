#!/usr/bin/env python3
"""
Test client for the VoxelGame agent interface.
Connects via WebSocket, receives handshake, prints state ticks,
and optionally sends test actions.

Usage:
    python3 test_agent_client.py [--action]
    
    --action  Send test actions after handshake (move forward, look around)
"""

import asyncio
import json
import sys

try:
    import websockets
except ImportError:
    print("Install websockets: pip3 install websockets")
    sys.exit(1)

URI = "ws://localhost:25566"
SEND_ACTIONS = "--action" in sys.argv

async def main():
    print(f"Connecting to {URI}...")
    
    try:
        async with websockets.connect(URI) as ws:
            print("Connected!")
            
            # Receive handshake
            hello = await ws.recv()
            hello_data = json.loads(hello)
            print(f"\n=== HELLO MESSAGE ===")
            print(f"Type: {hello_data.get('type')}")
            print(f"Version: {hello_data.get('version')}")
            print(f"Capabilities: {json.dumps(hello_data.get('capabilities', {}), indent=2)}")
            print(f"Operator guide: {hello_data.get('operator_guide', '')[:100]}...")
            print(f"Memory contract keys: {list(hello_data.get('memory_contract', {}).keys())}")
            print(f"Schema actions: {list(hello_data.get('schema', {}).get('actions', {}).keys())}")
            print(f"Hello message size: {len(hello)} bytes")
            
            if SEND_ACTIONS:
                print("\n=== SENDING TEST ACTIONS ===")
                
                # Wait a moment, then send actions
                await asyncio.sleep(0.5)
                
                # Look right
                action = {"type": "action_look", "yaw": 30.0, "pitch": 0.0}
                await ws.send(json.dumps(action))
                print(f"Sent: {action}")
                
                await asyncio.sleep(0.3)
                
                # Move forward for 2 seconds
                action = {"type": "action_move", "forward": 1.0, "strafe": 0.0, "duration": 2000}
                await ws.send(json.dumps(action))
                print(f"Sent: {action}")
                
                await asyncio.sleep(0.5)
                
                # Jump
                action = {"type": "action_jump"}
                await ws.send(json.dumps(action))
                print(f"Sent: {action}")
                
                await asyncio.sleep(0.3)
                
                # Select hotbar slot 3
                action = {"type": "action_hotbar_select", "slot": 2}
                await ws.send(json.dumps(action))
                print(f"Sent: {action}")
                
                await asyncio.sleep(0.3)
                
                # Attack (break block)
                action = {"type": "action_attack"}
                await ws.send(json.dumps(action))
                print(f"Sent: {action}")
            
            # Receive state ticks
            print("\n=== STATE TICKS ===")
            tick_count = 0
            max_ticks = 20  # show first 20 ticks
            
            while tick_count < max_ticks:
                msg = await ws.recv()
                data = json.loads(msg)
                
                if data.get("type") == "state":
                    tick_count += 1
                    pose = data.get("pose", {})
                    raycast = data.get("raycast", {})
                    ui = data.get("ui_state", {})
                    simscreen = data.get("simscreen", [])
                    
                    # Count simscreen dimensions
                    rows = len(simscreen)
                    cols = len(simscreen[0]) if rows > 0 else 0
                    
                    # Count non-sky cells
                    non_sky = 0
                    for row in simscreen:
                        for cell in row:
                            if cell[0] != 0:  # 0 = SKY
                                non_sky += 1
                    
                    print(f"Tick {data['tick']:4d} | "
                          f"pos=({pose.get('x',0):.1f}, {pose.get('y',0):.1f}, {pose.get('z',0):.1f}) | "
                          f"yaw={pose.get('yaw',0):.1f} pitch={pose.get('pitch',0):.1f} | "
                          f"ray={raycast.get('hit_type','?')}:{raycast.get('hit_id','?')} | "
                          f"screen={rows}x{cols} non_sky={non_sky} | "
                          f"slot={ui.get('hotbar_selected',0)} fly={ui.get('fly_mode',False)} | "
                          f"msg_size={len(msg)} bytes")
                else:
                    print(f"Unknown message type: {data.get('type')}")
            
            print(f"\nReceived {tick_count} state ticks. Test complete.")
            
    except ConnectionRefusedError:
        print(f"ERROR: Could not connect to {URI}")
        print("Make sure the game is running with --agent-server flag:")
        print("  ./gradlew run --args='--agent-server'")
    except Exception as e:
        print(f"ERROR: {e}")

if __name__ == "__main__":
    asyncio.run(main())
