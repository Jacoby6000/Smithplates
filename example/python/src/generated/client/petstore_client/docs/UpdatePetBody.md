# UpdatePetBody


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **str** |  | 
**status** | [**PetStatus**](PetStatus.md) |  | 
**species** | [**PetSpecies**](PetSpecies.md) |  | 
**category_id** | **str** |  | 
**owner_id** | **str** |  | [optional] 
**tag_count** | **float** |  | 
**tags** | **List[str]** |  | 
**attributes** | [**List[PetAttribute]**](PetAttribute.md) |  | 
**photo** | **bytearray** |  | [optional] 
**metadata** | **object** |  | [optional] 
**adopted_at** | **datetime** |  | [optional] 

## Example

```python
from petstore_client.models.update_pet_body import UpdatePetBody

# TODO update the JSON string below
json = "{}"
# create an instance of UpdatePetBody from a JSON string
update_pet_body_instance = UpdatePetBody.from_json(json)
# print the JSON string representation of the object
print(UpdatePetBody.to_json())

# convert the object into a dict
update_pet_body_dict = update_pet_body_instance.to_dict()
# create an instance of UpdatePetBody from a dict
update_pet_body_from_dict = UpdatePetBody.from_dict(update_pet_body_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


