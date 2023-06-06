const json = {
    id: "0x88e6A0c2dDD26FEEb64F039a2c41296FcB3f5640",
    ordered: false
}

const jsonString = JSON.stringify(json);

console.log(btoa(jsonString))